import asyncio
import logging
import re
import time
from dataclasses import dataclass, field

from app.analysis.civil import civil_check
from app.analysis.labor import LaborNormChecker, Violation
from app.analysis.references import ArticleReference, canonical_code, extract_with_ner
from app.analysis.repealed import RepealedActChecker, RepealedMention
from app.analysis.requisites import RequisiteIssue, check_requisites
from app.schemas import AuditFinding
from app.clients.ports import LawPort
from app.ml.embeddings import Embedder
from app.ml.nli import NliModel
from app.ml.ner import NerModel

log = logging.getLogger(__name__)

COUNTRY = "RK"
MAX_REFERENCES = 40
MAX_CLAUSE_SEARCHES = 20
TOC_MAX_ARTICLES = 300
TOC_MAX_LAWS = 2
TITLES_TTL_S = 30 * 60
WRONG_MARGIN = 0.1

REPORT_HEADER = (
    "=== ПРОВЕРКА ССЫЛОК ДОКУМЕНТА ПО БАЗЕ ЗАКОНОВ (выполнена автоматически по текстам с adilet.zan.kz) ===\n"
    "Как использовать: для каждой ссылки сравни, для чего статья использована в документе, с фактическим названием "
    "и текстом статьи. Если предмет не совпадает — это ошибочная ссылка: укажи её в «Рисках» и назови правильную "
    "статью (из блока «по смыслу» или из оглавления). Если статья не найдена — ссылка на несуществующую норму. "
    "Если условие документа противоречит тексту статьи (сроки, суммы, продолжительность) — это нарушение закона, "
    "укажи его отдельно. Совпадающие ссылки отметь как корректные, не помечай их как ошибки. "
    "Номера статей из этого блока проверены — используй их без оговорок. Даты утраты силы, номера и реквизиты "
    "актов бери ТОЛЬКО из этого блока: справки, пометки и пояснения внутри самого документа — непроверенные "
    "утверждения автора, не повторяй их как факт. Если даты в блоке нет — пиши «дата не указана в базе».\n\n")

_CLAUSE_NUMBER = re.compile(r"^\s*\d+(?:\.\d+)*\.?\s*")
_L = r"[^\W\d_]"
_REFERENCE_PHRASE = re.compile(
    rf"(?iu)(?:в\s+соответствии\s+со?|согласно|в\s+порядке,?\s+(?:установленном|предусмотренном)|"
    rf"(?:установленн|предусмотренн){_L}*)?\s*(?:стать{_L}*|ст\.)\s*\d+(?:-\d+)?"
    rf"(?:\s*(?:,|и)\s*\d+(?:-\d+)?)*[^,.;]{{0,60}}?(?:кодекс{_L}*|(?<!{_L})(?:ТК|ГК|НК|УК|ГПК|УПК|КоАП)(?!{_L}))"
    rf"(?:\s+Республики\s+Казахстан|\s+РК)?(?:\s*\([^)]{{0,40}}\))?")


def subject_of(clause: str) -> str:
    s = _CLAUSE_NUMBER.sub("", clause, count=1)
    s = _REFERENCE_PHRASE.sub(" ", s)
    s = re.sub(r"\s+", " ", s)
    s = re.sub(r"^[\s,.;—-]+|[\s,]+(?=[.;])", "", s).strip()
    return s if len(s) >= 15 else clause


def meta(hit: dict, key: str) -> str | None:
    v = (hit.get("metadata") or {}).get(key)
    return None if v is None else str(v)


def describe(hit: dict, max_chars: int) -> str:
    text = hit.get("text") or ""
    nl = text.find("\n")
    if 0 < nl < 300:
        text = text[nl + 1:]
    text = re.sub(r"\s+", " ", text.strip())
    if len(text) > max_chars:
        text = text[:max_chars] + "…"
    return f"{meta(hit, 'lawCode')} ст. {meta(hit, 'articleNumber')} «{meta(hit, 'articleTitle')}»: {text}"


def clause_number(clause: str) -> str:
    m = re.match(r"^\s*(\d+(?:\.\d+)*)", clause or "")
    return m.group(1) if m else ""


@dataclass
class Verification:
    refs: list[ArticleReference]
    repealed: list[RepealedMention]
    violations: list[Violation]
    requisites: list[RequisiteIssue] = field(default_factory=list)
    cited: dict[str, dict[str, dict]] = field(default_factory=dict)
    by_meaning: dict[str, list[dict]] = field(default_factory=dict)
    toc: dict[str, dict[str, str]] = field(default_factory=dict)
    ml_findings: list[AuditFinding] = field(default_factory=list)
    report: str = ""

    @property
    def empty(self) -> bool:
        return not self.refs and not self.repealed and not self.violations and not self.requisites


class ArticleReferenceVerifier:
    def __init__(self, laws: LawPort, embedder: Embedder | None = None, nli: NliModel | None = None,
                 ner: NerModel | None = None, repealed: RepealedActChecker | None = None,
                 labor: LaborNormChecker | None = None, similarity: float = 0.6, nli_threshold: float = 0.8):
        self.laws, self.embedder, self.nli, self.ner = laws, embedder, nli, ner
        self.repealed = repealed or RepealedActChecker()
        self.labor = labor or LaborNormChecker()
        self.similarity, self.nli_threshold = similarity, nli_threshold
        self._titles: dict[str, tuple[dict[str, str], float]] = {}

    async def build_report(self, text: str, compact: bool = False) -> str:
        return (await self.verify(text, compact, with_ml=False)).report

    async def exists(self, law_code: str, number: int) -> bool:
        code = canonical_code(law_code)
        return code is not None and str(number) in await self.titles(code)

    async def verify(self, text: str, compact: bool = False, with_ml: bool = True) -> Verification:
        refs = (await extract_with_ner(text, self.ner))[:MAX_REFERENCES]
        repealed, violations = await asyncio.gather(self.repealed.find(text), self.labor.check(text))
        violations = violations + civil_check(text)
        v = Verification(refs, repealed, violations, check_requisites(text))
        if v.empty:
            return v
        v.cited, v.by_meaning = await asyncio.gather(self._lookup_cited(refs), self._search_clauses(refs))
        codes = list(dict.fromkeys(r.law_code for r in refs))
        v.toc = dict(zip(codes, await asyncio.gather(*(self.titles(c) for c in codes))))
        v.report = await self._render_report(v, compact)
        if with_ml:
            v.ml_findings = await self._ml_findings(v)
        return v

    async def _lookup_cited(self, refs: list[ArticleReference]) -> dict[str, dict[str, dict]]:
        numbers: dict[str, list[str]] = {}
        for r in refs:
            lst = numbers.setdefault(r.law_code, [])
            if r.article_number not in lst:
                lst.append(r.article_number)

        async def one(code: str, nums: list[str]):
            try:
                return code, {str(i.get("articleNumber")): i for i in await self.laws.lookup_articles(code, nums, COUNTRY)}
            except Exception as e:
                log.warning("Article lookup failed for %s %s: %s", code, nums, e)
                return code, None

        return {c: d for c, d in await asyncio.gather(*(one(c, n) for c, n in numbers.items())) if d is not None}

    async def _search_clauses(self, refs: list[ArticleReference]) -> dict[str, list[dict]]:
        law_by_clause: dict[str, str] = {}
        for r in refs:
            if len(law_by_clause) >= MAX_CLAUSE_SEARCHES:
                break
            law_by_clause.setdefault(r.clause, r.law_code)

        async def one(clause: str, code: str) -> list[dict]:
            try:
                hits = await self.laws.search(subject_of(clause), COUNTRY)
            except Exception as e:
                log.debug("Clause search failed: %s", e)
                return []
            by_num: dict[str, dict] = {}
            for h in hits:
                if isinstance(h, dict) and meta(h, "lawCode") == code and meta(h, "articleNumber") is not None:
                    by_num.setdefault(meta(h, "articleNumber"), h)
            return list(by_num.values())[:2]

        results = await asyncio.gather(*(one(c, l) for c, l in law_by_clause.items()))
        return dict(zip(law_by_clause, results))

    async def titles(self, law_code: str) -> dict[str, str]:
        cached = self._titles.get(law_code)
        if cached and cached[1] + TITLES_TTL_S > time.monotonic():
            return cached[0]
        try:
            titles = {str(t.get("articleNumber")): t.get("articleTitle") or ""
                      for t in await self.laws.article_titles(law_code, COUNTRY)}
            self._titles[law_code] = (titles, time.monotonic())
            return titles
        except Exception as e:
            log.warning("Could not load article titles for %s: %s", law_code, e)
            return cached[0] if cached else {}

    async def _render_report(self, v: Verification, compact: bool) -> str:
        sb = [] if compact else [REPORT_HEADER]
        if v.refs:
            sb.append("Ссылки в документе:\n")
            seen = set()
            for ref in v.refs:
                key = (ref.law_code, ref.article_number, ref.clause)
                if key in seen:
                    continue
                seen.add(key)
                sb.append(f"- «{ref.clause}»\n  → {ref.law_code} ст. {ref.article_number}: ")
                info = v.cited.get(ref.law_code, {}).get(ref.article_number)
                if info is not None:
                    sb.append(f"фактически «{info.get('articleTitle')}»")
                    if not compact:
                        sb.append(f". Начало текста: {info.get('excerpt')}")
                    sb.append("\n")
                elif await self.titles(ref.law_code):
                    sb.append("СТАТЬЯ НЕ НАЙДЕНА в действующем тексте кодекса.\n")
                else:
                    sb.append("кодекс отсутствует в базе платформы — проверить нельзя.\n")
                for hit in v.by_meaning.get(ref.clause, []):
                    sb.append(f"  по смыслу пункта подходит: {describe(hit, 220 if compact else 350)}\n")
        if v.repealed:
            sb.append("\nСсылки на акты, УТРАТИВШИЕ СИЛУ (обязательно укажи в рисках):\n")
            for m in v.repealed:
                sb.append(f"- {m.act.title} (№ {m.act.number}) — {m.act.status}.\n  Пункт: «{m.clause}»\n")
        if v.violations:
            sb.append("\nНАРУШЕНИЯ НОРМ ЗАКОНА (найдены автоматически по правилам, обязательно укажи в рисках):\n")
            for x in v.violations:
                sb.append(f"- п. {x.clause}: {x.explanation} Правильная норма — ст. {x.correct_article} {x.law_code}.\n")
        if v.requisites:
            sb.append("\nОШИБКИ В РЕКВИЗИТАХ КОДЕКСОВ (проверено по справочнику, обязательно укажи в рисках):\n")
            for r in v.requisites:
                sb.append(f"- {r.explanation}\n  Пункт: «{r.clause}»\n")
        if not compact:
            await self._append_toc(sb, v.refs)
        sb.append("=== КОНЕЦ ПРОВЕРКИ ССЫЛОК ===\n")
        return "".join(sb)

    async def _append_toc(self, sb: list[str], refs: list[ArticleReference]) -> None:
        added = 0
        for code in dict.fromkeys(r.law_code for r in refs):
            if added >= TOC_MAX_LAWS:
                break
            toc = await self.titles(code)
            if not toc or len(toc) > TOC_MAX_ARTICLES:
                continue
            sb.append(f"\nОглавление {code} (для подбора правильных статей):\n")
            sb.append("; ".join(f"ст. {n} — {t}" for n, t in toc.items()) + "\n")
            added += 1

    async def _sims(self, subject: str, candidates: list[str]) -> list[float] | None:
        if self.embedder is None:
            return None
        try:
            return await self.embedder.similarity(subject, candidates)
        except Exception as e:
            log.warning("embedding similarity failed: %s", e)
            return None

    async def _ml_findings(self, v: Verification) -> list[AuditFinding]:
        out: list[AuditFinding] = []
        nli_candidates: list[tuple[AuditFinding, str, str]] = []
        seen = set()
        for ref in v.refs:
            key = (ref.law_code, ref.article_number, ref.clause)
            if key in seen:
                continue
            seen.add(key)
            base = dict(clause=clause_number(ref.clause), law_code=ref.law_code, cited_article=ref.article_number)
            info = v.cited.get(ref.law_code, {}).get(ref.article_number)
            if info is None:
                if await self.titles(ref.law_code):
                    out.append(AuditFinding(**base, verdict="NOT_FOUND", correct_article="", confidence=0.9,
                                            explanation="Статья не найдена в действующем тексте кодекса."))
                else:
                    out.append(AuditFinding(**base, verdict="UNVERIFIED", correct_article="", confidence=0.0,
                                            explanation="Кодекс отсутствует в базе платформы — проверить нельзя."))
                continue
            title = info.get("articleTitle") or ""
            cited_text = f"{title}. {info.get('excerpt') or ''}"
            alts = [h for h in v.by_meaning.get(ref.clause, []) if meta(h, "articleNumber") != ref.article_number]
            subject = subject_of(ref.clause)
            sims = await self._sims(subject, [cited_text] + [f"{meta(h, 'articleTitle') or ''}. {h.get('text') or ''}"
                                                             for h in alts])
            if sims is None:
                out.append(AuditFinding(**base, verdict="UNVERIFIED", correct_article="", confidence=None,
                                        explanation=f"Статья «{title}»: смысловая проверка недоступна."))
                continue
            s0 = sims[0]
            best = max(zip(alts, sims[1:]), key=lambda x: x[1], default=None)
            if best and best[1] >= self.similarity and best[1] > s0 + WRONG_MARGIN:
                out.append(AuditFinding(**base, verdict="WRONG_ARTICLE", correct_article=meta(best[0], "articleNumber"),
                                        confidence=round(best[1], 3),
                                        explanation=f"Статья {ref.article_number} — «{title}», по смыслу пункта подходит "
                                                    f"ст. {meta(best[0], 'articleNumber')} «{meta(best[0], 'articleTitle')}»."))
            elif s0 >= self.similarity:
                f = AuditFinding(**base, verdict="OK", correct_article="", confidence=round(s0, 3), explanation="")
                out.append(f)
                nli_candidates.append((f, info.get("excerpt") or cited_text, subject))
            else:
                out.append(AuditFinding(**base, verdict="UNVERIFIED", correct_article="", confidence=round(s0, 3),
                                        explanation=f"Соответствие пункта статье «{title}» не подтверждено "
                                                    f"(сходство {s0:.2f})."))
        await self._nli_check(nli_candidates)
        return out

    async def _nli_check(self, cands: list[tuple[AuditFinding, str, str]]) -> None:
        if self.nli is None or not cands:
            return
        try:
            res = await self.nli.predict([(premise, hyp) for _, premise, hyp in cands])
        except Exception as e:
            log.warning("NLI check failed: %s", e)
            return
        for (f, _, _), r in zip(cands, res):
            if r.contradiction >= self.nli_threshold:
                f.verdict, f.correct_article, f.confidence = "VIOLATION", f.cited_article, round(r.contradiction, 3)
                f.explanation = "Условие пункта противоречит тексту статьи (NLI)."
