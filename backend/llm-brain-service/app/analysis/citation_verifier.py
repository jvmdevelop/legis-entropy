import asyncio
import logging

from pydantic import BaseModel

from app.analysis.verifier import COUNTRY, Verification, clause_number as clause_key
from app.clients.ports import LawPort
from app.llm.client import Completer
from app.schemas import AuditFinding

log = logging.getLogger(__name__)

CHECKED = {"WRONG_ARTICLE", "VIOLATION", "NOT_FOUND"}
SOURCES = {"llm", "ml"}
MAX_FINDINGS = 15
ARTICLE_CHARS = 4000
STATUSES = ("CONFIRMED", "REFUTED", "INSUFFICIENT")

SYSTEM = ("Ты — независимый юрист-проверяющий (Республика Казахстан). Другой аудитор нашёл проблемы в договоре. "
          "Твоя задача — ПОПЫТАТЬСЯ ОПРОВЕРГНУТЬ каждую находку, опираясь только на приведённые тексты статей. "
          "Не соглашайся по умолчанию. Статусы: CONFIRMED — тексты статей подтверждают находку; REFUTED — тексты "
          "противоречат находке (например, процитированная статья на самом деле регулирует предмет пункта, или "
          "предложенная статья про другое); INSUFFICIENT — текстов недостаточно для вывода. "
          "Не используй знания вне приведённых текстов. Верни JSON {items:[{i, status, reason}]}, reason — "
          "одно предложение со ссылкой на текст статьи.")


PURPOSE_SYSTEM = (
    "Ты — юрист РК, проверяешь точность ссылок в договоре. Для каждой ссылки: для ЧЕГО пункт ссылается на статью "
    "(основание какого права, обязанности, порядка) и регулирует ли статья ИМЕННО это по приведённому тексту. "
    "EXACT — статья прямо регулирует то, для чего на неё сослались (это вариант по умолчанию; формулировки могут "
    "не совпадать дословно); IMPRECISE — статья лишь смежная с предметом пункта и не является его прямым основанием; "
    "UNRELATED — статья про другое. Ставь IMPRECISE/UNRELATED только при явном расхождении с текстом статьи. "
    "Верни JSON {items:[{i, status, reason}]}, reason — одно предложение: что регулирует статья и для чего её привели.")
PURPOSE_STATUSES = ("EXACT", "IMPRECISE", "UNRELATED")
MAX_PURPOSE = 20


class _Item(BaseModel):
    i: int
    status: str
    reason: str = ""


class _Items(BaseModel):
    items: list[_Item] = []


def _article(info: dict | None) -> str:
    if not info:
        return "(текст не найден в базе)"
    text = f"«{info.get('articleTitle') or ''}». {info.get('excerpt') or info.get('text') or ''}".strip()
    return text[:ARTICLE_CHARS]


class CitationVerifier:
    def __init__(self, laws: LawPort, llm: Completer | None, similarity: float = 0.6):
        self.laws, self.llm, self.similarity = laws, llm, similarity

    async def _lookup(self, wanted: dict[str, set[str]], known: dict[str, dict[str, dict]] | None = None
                      ) -> dict[str, dict[str, dict]]:
        out: dict[str, dict[str, dict]] = {}

        async def one(code: str, nums: list[str]):
            try:
                return code, await self.laws.lookup_articles(code, nums, COUNTRY, ARTICLE_CHARS)
            except Exception as e:
                log.debug("citation lookup failed %s %s: %s", code, nums, e)
                return code, []

        for code, items in await asyncio.gather(*(one(c, sorted(n)) for c, n in wanted.items() if n)):
            for it in items or []:
                out.setdefault(code, {})[str(it.get("articleNumber"))] = it
        for code, arts in (known or {}).items():
            for num, info in arts.items():
                out.setdefault(code, {}).setdefault(num, info)
        return out

    async def verify(self, findings: list[AuditFinding], v: Verification) -> list[AuditFinding]:
        todo = [f for f in findings if f.verdict in CHECKED and f.source in SOURCES][:MAX_FINDINGS]
        if not todo:
            return findings
        wanted: dict[str, set[str]] = {}
        for f in todo:
            wanted.setdefault(f.law_code or "", set()).update(x for x in (f.cited_article, f.correct_article) if x)
        texts = await self._lookup(wanted, v.cited)
        clauses = {}
        for r in v.refs:
            clauses.setdefault((r.law_code, r.article_number), r.clause)

        prompt, idx = [], []
        for f in todo:
            arts = texts.get(f.law_code or "", {})
            cited, correct = arts.get(f.cited_article or ""), arts.get(f.correct_article or "")
            if cited is None and correct is None:
                f.verification, f.verification_note = "INSUFFICIENT", "Тексты статей недоступны в базе."
                continue
            clause = clauses.get((f.law_code, f.cited_article), "")
            block = [f"[{len(idx)}] Находка: {f.verdict}. {f.explanation or ''}",
                     f"Пункт договора {f.clause}: «{clause}»",
                     f"Процитированная ст. {f.cited_article} {f.law_code}: {_article(cited)}"]
            if f.correct_article and f.correct_article != f.cited_article:
                block.append(f"Предложенная ст. {f.correct_article} {f.law_code}: {_article(correct)}")
            prompt.append("\n".join(block))
            idx.append(f)
        if not idx:
            return findings
        result = await self._ask("\n\n".join(prompt))
        for i, f in enumerate(idx):
            item = result.get(i)
            if item is None:
                f.verification, f.verification_note = "INSUFFICIENT", "Проверяющий не дал ответа."
                continue
            f.verification, f.verification_note = item
            if f.verification == "REFUTED":
                f.explanation = (f"Находка опровергнута проверкой: {f.verification_note} "
                                 f"(исходно: {f.verdict}. {f.explanation or ''})").strip()
                f.verdict, f.correct_article = "UNVERIFIED", ""
        return findings

    async def check_purpose(self, findings: list[AuditFinding], v: Verification) -> list[AuditFinding]:
        clauses = {}
        for r in v.refs:
            clauses.setdefault((clause_key(r.clause), r.law_code, r.article_number), r.clause)
        todo = []
        for f in findings:
            info = v.cited.get(f.law_code or "", {}).get(f.cited_article or "")
            clause = clauses.get((f.clause or "", f.law_code, f.cited_article))
            weak = f.confidence is None or f.confidence < self.similarity
            if f.verdict in ("OK", "UNVERIFIED") and weak and f.source != "rule" and info is not None and clause:
                todo.append((f, clause, info))
        todo = todo[:MAX_PURPOSE]
        if not todo:
            return findings
        wanted: dict[str, set[str]] = {}
        for f, _, _ in todo:
            wanted.setdefault(f.law_code or "", set()).add(f.cited_article or "")
        full = await self._lookup(wanted, v.cited)
        todo = [(f, clause, full.get(f.law_code or "", {}).get(f.cited_article or "", info)) for f, clause, info in todo]
        result = await self._purpose(todo)
        flagged = [i for i, (st, _) in result.items() if st in ("IMPRECISE", "UNRELATED")]
        if flagged:
            again = await self._purpose([todo[i] for i in flagged])
            for k, i in enumerate(flagged):
                if again.get(k, (None, ""))[0] not in ("IMPRECISE", "UNRELATED"):
                    result[i] = ("EXACT", result[i][1]) if todo[i][0].verdict == "OK" else (None, "")
        for i, (f, _, _) in enumerate(todo):
            status, reason = result.get(i, (None, ""))
            note = "Проверка назначения ссылки по тексту статьи (LLM), сверьте вручную."
            if status == "IMPRECISE":
                f.verdict, f.source, f.explanation = "IMPRECISE", "llm", f"Ссылка неточная: {reason}"
                f.verification, f.verification_note = "INSUFFICIENT", note
            elif status == "UNRELATED":
                f.verdict, f.source, f.correct_article = "WRONG_ARTICLE", "llm", ""
                f.explanation = f"Статья не регулирует предмет пункта: {reason}"
                f.verification, f.verification_note = "INSUFFICIENT", note
            elif status == "EXACT" and f.verdict == "UNVERIFIED":
                f.verdict, f.explanation = "OK", ""
                f.verification, f.verification_note = "CONFIRMED", reason
        return findings

    async def _purpose(self, items: list[tuple[AuditFinding, str, dict]]) -> dict[int, tuple[str, str]]:
        body = "\n\n".join(f"[{i}] Пункт {f.clause}: «{clause}»\nСт. {f.cited_article} {f.law_code}: {_article(info)}"
                             for i, (f, clause, info) in enumerate(items))
        return await self._ask(body, PURPOSE_SYSTEM, PURPOSE_STATUSES)

    async def _ask(self, body: str, system: str = SYSTEM, statuses: tuple[str, ...] = STATUSES) -> dict[int, tuple[str, str]]:
        if self.llm is None:
            return {}
        try:
            out = await self.llm.structured(system, body, _Items)
        except Exception as e:
            log.warning("citation verifier LLM failed: %s", e)
            return {}
        res = {}
        for it in (out.items if out else []):
            status = it.status.strip().upper()
            if status in statuses:
                res[it.i] = (status, it.reason.strip())
        return res
