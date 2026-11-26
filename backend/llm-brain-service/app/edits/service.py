import hashlib
import logging
import re
from dataclasses import dataclass

from pydantic import BaseModel

from app.analysis.audit import ReferenceAuditService
from app.clients.ports import DocumentPort
from app.llm.client import Completer
from app.schemas import AuditFinding, EditApplyResponse, EditProposal, EditSkip, EditSuggestResponse

log = logging.getLogger(__name__)

MAX_LLM_EDITS = 12
MAX_FRAGMENT = 600
_L = r"[^\W\d_]"
_CLAUSE_LINE = re.compile(r"(?m)^[ \t]*(\d+(?:\.\d+)+)\.?\s")

SYSTEM = ("Ты — юрист РК и редактор договоров. Для каждой проблемы предложи МИНИМАЛЬНУЮ правку пункта договора. "
          "original — точная непрерывная цитата из текста пункта (символ в символ), которую нужно заменить; "
          "replacement — новый текст вместо неё, в том же стиле договора, по-русски. Меняй только то, что нужно для "
          "устранения проблемы (число, ссылку на норму, недостающее условие). Не придумывай номера статей и законов, "
          "которых нет в описании проблемы. Если правку предложить нельзя — пропусти пункт. "
          "Верни JSON {edits:[{i, original, replacement, reason}]}, reason — одно предложение.")


class _Edit(BaseModel):
    i: int
    original: str
    replacement: str
    reason: str = ""


class _Edits(BaseModel):
    edits: list[_Edit] = []


class _Rewrite(BaseModel):
    replacement: str = ""
    reason: str = ""


REWRITE_SYSTEM = ("Ты — юрист РК и редактор договоров. Пользователь выделил фрагмент договора. Предложи улучшенную "
                  "редакцию ТОЛЬКО этого фрагмента: юридически точную, соответствующую законодательству РК, в стиле "
                  "договора. Не придумывай номера статей и законов, если не уверен. Если фрагмент уже корректен и "
                  "инструкции нет — верни replacement пустым. Верни JSON {replacement, reason}, reason — одно "
                  "предложение: что и почему изменено.")
MAX_REWRITE = 1500


def clause_lines(text: str) -> dict[str, tuple[int, int]]:
    out: dict[str, tuple[int, int]] = {}
    for m in _CLAUSE_LINE.finditer(text):
        end = text.find("\n", m.start())
        out.setdefault(m.group(1), (m.start(), len(text) if end < 0 else end))
    return out


def keep_end_punctuation(original: str, replacement: str) -> str:
    end = original.rstrip()[-1:]
    if end in ".;" and replacement.rstrip()[-1:] not in ".;!?":
        return replacement.rstrip() + end
    return replacement


def occurrence(text: str, fragment: str, pos: int) -> int:
    n, i = 0, text.find(fragment)
    while 0 <= i < pos:
        n, i = n + 1, text.find(fragment, i + 1)
    return n


def _pid(clause: str, original: str, replacement: str) -> str:
    return hashlib.sha1(f"{clause}\x00{original}\x00{replacement}".encode()).hexdigest()[:12]


def _proposal(f: AuditFinding, original: str, replacement: str, reason: str, source: str) -> EditProposal:
    basis = f"ст. {f.correct_article} {f.law_code}" if f.verdict == "WRONG_ARTICLE" and f.correct_article else None
    return EditProposal(id=_pid(f.clause or "", original, replacement), clause=f.clause or "", original=original,
                        replacement=replacement, reason=reason, verdict=f.verdict, law_basis=basis, source=source)


def article_swap(f: AuditFinding, clause: str) -> tuple[str, str] | None:
    if f.verdict != "WRONG_ARTICLE" or not f.correct_article or not f.cited_article:
        return None
    m = re.search(rf"(?iu)(?:стать{_L}*|ст\.)\s*{re.escape(f.cited_article)}(?![\d-])", clause)
    if not m:
        return None
    original = m.group(0)
    return original, original[: len(original) - len(f.cited_article)] + f.correct_article


def requisites_swap(f: AuditFinding, clause: str) -> tuple[str, str] | None:
    if f.verdict != "WRONG_REQUISITES" or not f.correct_article or not f.cited_article:
        return None
    num, roman = f.cited_article.split("-", 1)
    m = re.search(rf"(?iu)(?:№|N)\s*{num}\s*-\s*{roman}(?!{_L})", clause)
    if not m:
        return None
    return m.group(0), m.group(0)[: m.group(0).index(num)] + f.correct_article


def _deterministic_ok(f: AuditFinding) -> bool:
    return f.source in ("rule", "registry") or f.verification == "CONFIRMED" or f.verdict == "WRONG_REQUISITES"


@dataclass
class _Span:
    start: int
    end: int
    proposal: EditProposal


class DocumentEditService:
    def __init__(self, auditor: ReferenceAuditService, documents: DocumentPort, llm: Completer | None, graph=None):
        self.auditor, self.documents, self.llm, self.graph = auditor, documents, llm, graph

    async def suggest_for_text(self, text: str) -> tuple[list[EditProposal], str]:
        proposals, audit = await self.suggest_with_audit(text)
        return proposals, audit.summary

    async def suggest_with_audit(self, text: str):
        audit = await self.auditor.audit(text)
        return await self.proposals(text, audit.findings), audit

    async def proposals(self, text: str, findings: list[AuditFinding]) -> list[EditProposal]:
        lines = clause_lines(text)
        spans: list[_Span] = []
        llm_todo: list[tuple[AuditFinding, str, int]] = []
        for f in findings:
            if not f.is_problem or f.verdict == "UNVERIFIED" or f.clause not in lines:
                continue
            start, end = lines[f.clause]
            clause = text[start:end]
            swap = (article_swap(f, clause) or requisites_swap(f, clause)) if _deterministic_ok(f) else None
            if swap:
                reason = (f.explanation or "").split("  \n")[0] or "Исправлена ссылка."
                self._add(spans, start + clause.index(swap[0]), swap, _proposal(f, *swap, reason, "rule"))
            elif f.verdict in ("VIOLATION", "REPEALED", "IMPRECISE", "WRONG_ARTICLE"):
                llm_todo.append((f, clause, start))
        for f, clause, start, edit in await self._llm_edits(llm_todo[:MAX_LLM_EDITS]):
            pos = clause.find(edit.original)
            if pos < 0 or not edit.original.strip() or edit.original == edit.replacement:
                continue
            replacement = keep_end_punctuation(edit.original, edit.replacement)
            self._add(spans, start + pos, (edit.original, replacement),
                      _proposal(f, edit.original, replacement, edit.reason, "llm"))
        return [s.proposal for s in sorted(spans, key=lambda s: s.start)]

    @staticmethod
    def _add(spans: list[_Span], start: int, swap: tuple[str, str], p: EditProposal) -> None:
        end = start + len(swap[0])
        if any(s.start < end and start < s.end for s in spans):
            return
        spans.append(_Span(start, end, p))

    async def _llm_edits(self, todo: list[tuple[AuditFinding, str, int]]):
        if not todo or self.llm is None:
            return []
        body = "\n\n".join(
            f"[{i}] Пункт: «{clause}»\nПроблема ({f.verdict}): {f.explanation or ''}"
            + (f" Правильная норма: ст. {f.correct_article} {f.law_code}." if f.correct_article else "")
            for i, (f, clause, _) in enumerate(todo))
        try:
            out = await self.llm.structured(SYSTEM, body, _Edits)
        except Exception as e:
            log.warning("edit suggestion LLM failed: %s", e)
            return []
        res = []
        for e in (out.edits if out else []):
            if 0 <= e.i < len(todo) and len(e.original) <= MAX_FRAGMENT and len(e.replacement) <= 2 * MAX_FRAGMENT:
                f, clause, start = todo[e.i]
                res.append((f, clause, start, e))
        return res

    async def rewrite(self, text: str, original: str, instruction: str | None = None) -> EditProposal | None:
        original = original.strip()
        if not original or len(original) > MAX_REWRITE or original not in text or self.llm is None:
            return None
        pos = text.find(original)
        clause_no, clause = "", original
        for no, (a, b) in clause_lines(text).items():
            if a <= pos < b:
                clause_no, clause = no, text[a:b]
                break
        body = (f"Пункт договора: «{clause}»\nВыделенный фрагмент: «{original}»\n"
                + (f"Инструкция пользователя: {instruction.strip()}\n" if instruction and instruction.strip() else ""))
        try:
            out = await self.llm.structured(REWRITE_SYSTEM, body, _Rewrite)
        except Exception as e:
            log.warning("rewrite LLM failed: %s", e)
            return None
        replacement = keep_end_punctuation(original, (out.replacement if out else "").strip())
        if not replacement or replacement == original or len(replacement) > 3 * MAX_REWRITE:
            return None
        return EditProposal(id=_pid(clause_no, original, replacement), clause=clause_no, original=original,
                            replacement=replacement, reason=(out.reason or "").strip(), verdict="SUGGESTION",
                            source="llm")

    @staticmethod
    def apply_to_text(text: str, proposals: list[EditProposal]) -> tuple[str, list[str], list[EditSkip]]:
        lines = clause_lines(text)
        located: list[tuple[int, EditProposal]] = []
        skipped: list[EditSkip] = []
        for p in proposals:
            pos = -1
            if p.clause in lines:
                a, b = lines[p.clause]
                i = text.find(p.original, a, b)
                pos = i if i >= 0 else -1
            if pos < 0 and text.count(p.original) == 1:
                pos = text.find(p.original)
            if pos < 0:
                skipped.append(EditSkip(id=p.id, reason="Исходный фрагмент не найден в текущем тексте документа."))
                continue
            if any(q < pos + len(p.original) and pos < q + len(o.original) for q, o in located):
                skipped.append(EditSkip(id=p.id, reason="Пересекается с другой правкой."))
                continue
            located.append((pos, p))
        out = text
        for pos, p in sorted(located, key=lambda x: -x[0]):
            out = out[:pos] + p.replacement + out[pos + len(p.original):]
        return out, [p.id for _, p in sorted(located, key=lambda x: x[0])], skipped

    async def apply(self, document_id: str, user_id: str, proposals: list[EditProposal],
                    title: str | None = None, in_place: bool = False, graph_id: str | None = None) -> EditApplyResponse:
        text = await self.documents.text(document_id, user_id)
        if not text:
            raise LookupError("document text unavailable")
        new_text, applied, skipped = self.apply_to_text(text, proposals)
        if not applied:
            return EditApplyResponse(document_id=None, file_name=None, applied=[], skipped=skipped)
        by_id, pos = {p.id: p for p in proposals}, self._positions(text, proposals)
        edits = [{"original": by_id[i].original, "replacement": by_id[i].replacement,
                  "occurrence": occurrence(text, by_id[i].original, pos[i])} for i in applied]
        if in_place:
            return await self._apply_in_place(document_id, user_id, edits, applied, skipped, graph_id)
        try:
            r = await self.documents.edited_copy(document_id, user_id, edits, title)
        except Exception as e:
            log.warning("format-preserving copy failed for %s, saving as text: %s", document_id, e)
            r = None
        if r and r.get("documentId"):
            done = {applied[k] for k in r.get("applied") or []}
            skipped += [EditSkip(id=applied[s["index"]], reason=s.get("reason") or "")
                        for s in r.get("skipped") or [] if 0 <= s.get("index", -1) < len(applied)]
            return EditApplyResponse(document_id=r["documentId"], file_name=r.get("fileName"),
                                     applied=[i for i in applied if i in done], skipped=skipped)
        name = title or await self._edited_name(document_id, user_id)
        new_id = await self.documents.upload(user_id, name, new_text.encode("utf-8"), "text/plain; charset=utf-8")
        return EditApplyResponse(document_id=new_id, file_name=name, applied=applied, skipped=skipped)

    async def _apply_in_place(self, document_id: str, user_id: str, edits: list[dict], applied: list[str],
                              skipped: list[EditSkip], graph_id: str | None) -> EditApplyResponse:
        r = await self.documents.edit_in_place(document_id, user_id, edits)
        if not r or not r.get("documentId"):
            return EditApplyResponse(document_id=None, file_name=None, applied=[], skipped=skipped, in_place=True)
        done = {applied[k] for k in r.get("applied") or [] if 0 <= k < len(applied)}
        skipped = skipped + [EditSkip(id=applied[x["index"]], reason=x.get("reason") or "")
                             for x in r.get("skipped") or [] if 0 <= x.get("index", -1) < len(applied)]
        removed = None
        if graph_id and self.graph is not None:
            try:
                g = await self.graph.document_revised(graph_id, document_id, r.get("fileName"), user_id)
                removed = int((g or {}).get("removedLinks") or 0)
            except Exception as e:
                log.warning("graph refresh after in-place edit of %s failed: %s", document_id, e)
        return EditApplyResponse(document_id=document_id, file_name=r.get("fileName"),
                                 applied=[i for i in applied if i in done], skipped=skipped, in_place=True,
                                 graph_links_removed=removed)

    @staticmethod
    def _positions(text: str, proposals: list[EditProposal]) -> dict[str, int]:
        lines = clause_lines(text)
        out = {}
        for p in proposals:
            pos = -1
            if p.clause in lines:
                pos = text.find(p.original, *lines[p.clause])
            if pos < 0 and text.count(p.original) == 1:
                pos = text.find(p.original)
            out[p.id] = pos
        return out

    async def _edited_name(self, document_id: str, user_id: str) -> str:
        try:
            original = await self.documents.file_name(document_id, user_id) or "документ"
        except Exception:
            original = "документ"
        stem = re.sub(r"\.[A-Za-z0-9]{1,5}$", "", original)
        return f"{stem} (исправлено).txt"
