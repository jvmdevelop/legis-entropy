import inspect
import logging
import re
import time
from collections import Counter
from dataclasses import dataclass

from app.ml import text as T
from app.ml.embeddings import Embedder
from app.ml.ner import NerModel, resolve_law
from app.ml.nli import NliModel
from app.pipeline.context import DocumentContext
from app.pipeline.types import ContextPlan, RetrievalMode
from app.clients.ports import LawPort
from app.ports import ReferenceReports

log = logging.getLogger(__name__)

LAW_REFERENCE = re.compile(r"(?i)(?<![^\W\d_])(?:стать[яиеюей]|статей|ст\.?\s*\d|закон|приказ|кодекс|постановлени|"
                           r"положени|инструкци|(?:[ГАТС]К (?:РК|РФ)|ФЗ|РФ|ГК|УК|ПК|ЖК)(?![^\W\d_]))")
STATUTE_FULL = re.compile(r"(?i)(?<![^\W\d_])((?:стать[яиеею]|статей|ст\.?))\s*(\d+)")
LAW_CODE = re.compile(r"(?i)(?<![^\W\d_])(?:ГПК|УПК|АППК|КоАП|УК|ГК|ТК|НК|СК|ЖК|ЗК|ПК|ЭК)(?:\s+(?:РК|РФ))?"
                      r"(?![^\W\d_])")
UNVERIFIED_MARK = " _(не подтверждено)_"
CLAIM_ENTITY_LABELS = {"law", "article", "date", "sum", "term"}
MAX_CLAIMS = 8
MIN_CLAIM_LENGTH = 25
DEFAULT_COUNTRY = "RK"
TITLES_TTL_S = 30 * 60

SHORT_ANSWER_NOTE = "\n\nПримечание: retrieval-контекст был найден, но ответ неполный. Проверьте вывод вручную."
UNGROUNDED_DISCLAIMER = ("\n\n> ℹ️ Это аналитический ответ без обращения к retrieval-базе. "
                         "Перед использованием в правовом документе перепроверьте указанные нормы и номера статей "
                         "по официальному источнику (adilet.zan.kz).")
EMPTY_ANSWER = "Не удалось получить ответ от LLM."


@dataclass(frozen=True)
class UngroundedRef:
    number: int
    label: str
    law_code: str | None
    start: int
    end: int


def _norm_code(code: str | None) -> str:
    return re.sub(r"[^\w]|_", "", (code or "").upper())


def _codes_match(a: str, b: str) -> bool:
    return a == b or a.startswith(b) or b.startswith(a)


class ArticleIndex:
    def __init__(self, laws: LawPort, verifier: ReferenceReports | None = None):
        self.laws = laws
        self.verifier = verifier
        self._cache: dict[str, tuple[float, set[str]]] = {}

    @staticmethod
    def canonical(law_code: str | None) -> str | None:
        if not law_code or not law_code.strip():
            return None
        code = resolve_law(law_code)
        if code is None and not re.search(r"(?i)\b(рк|рф)\b", law_code):
            code = resolve_law(law_code + " РК")
        return code

    async def _numbers(self, code: str) -> set[str]:
        hit = self._cache.get(code)
        if hit and time.monotonic() - hit[0] < TITLES_TTL_S:
            return hit[1]
        try:
            rows = await self.laws.article_titles(code, DEFAULT_COUNTRY) or []
            nums = {str(r.get("articleNumber")) for r in rows if isinstance(r, dict) and r.get("articleNumber")}
            self._cache[code] = (time.monotonic(), nums)
            return nums
        except Exception as e:
            log.warning("Could not load article titles for %s: %s", code, e)
            return hit[1] if hit else set()

    async def exists(self, law_code: str | None, number: int) -> bool:
        if self.verifier is not None and hasattr(self.verifier, "exists"):
            try:
                r = self.verifier.exists(law_code, number)
                return bool(await r if inspect.isawaitable(r) else r)
            except Exception as e:
                log.debug("verifier.exists failed: %s", e)
        code = self.canonical(law_code)
        return code is not None and str(number) in await self._numbers(code)


class HallucinationDetector:
    def __init__(self, articles: ArticleIndex | None = None):
        self.articles = articles

    @staticmethod
    def retrieved_article_numbers(ctx: DocumentContext | None) -> set[int]:
        if ctx is None:
            return set()
        return {c.article_number for c in ctx.citations if c.article_number is not None and c.article_number > 0}

    def has_grounding_context(self, ctx: DocumentContext | None) -> bool:
        return bool(self.retrieved_article_numbers(ctx))

    @staticmethod
    def has_law_references(answer: str | None) -> bool:
        return bool(answer) and LAW_REFERENCE.search(answer) is not None

    @staticmethod
    def nearby_law_code(answer: str, start: int, end: int) -> str | None:
        lo, hi = max(0, start - 80), min(len(answer), end + 80)
        local = start - lo
        best, best_d = None, None
        for m in LAW_CODE.finditer(answer[lo:hi]):
            d = local - m.end() if m.end() <= local else max(0, m.start() - local)
            if best_d is None or d < best_d:
                best, best_d = m.group(), d
        return best

    @staticmethod
    def dominant_law_code(answer: str) -> str | None:
        counts: Counter = Counter()
        spelling: dict[str, str] = {}
        for m in LAW_CODE.finditer(answer):
            key = re.sub(r"(РК|РФ)$", "", _norm_code(m.group()))
            counts[key] += 1
            spelling.setdefault(key, m.group())
        return spelling[counts.most_common(1)[0][0]] if counts else None

    @staticmethod
    def line_law_code(answer: str, pos: int) -> str | None:
        ls = answer.rfind("\n", 0, pos) + 1
        le = answer.find("\n", pos)
        found = None
        for m in LAW_CODE.finditer(answer[ls: le if le >= 0 else len(answer)]):
            if found is not None and _norm_code(found) != _norm_code(m.group()):
                return None
            found = m.group()
        return found

    def is_grounded(self, number: int, answer: str, start: int, end: int, ctx: DocumentContext | None) -> bool:
        same = [c for c in (ctx.citations if ctx else []) if c.article_number == number]
        if not same:
            return False
        explicit = self.nearby_law_code(answer, start, end)
        if explicit is not None:
            mention = _norm_code(explicit)
            canon = ArticleIndex.canonical(explicit)
            return any(c.law_code and (_codes_match(mention, _norm_code(c.law_code)) or
                                       (canon is not None and _norm_code(canon) == _norm_code(c.law_code)))
                       for c in same)
        return len({_norm_code(c.law_code) for c in same if _norm_code(c.law_code)}) <= 1

    async def ungrounded_refs(self, answer: str | None, ctx: DocumentContext | None) -> list[UngroundedRef]:
        if not answer or not answer.strip():
            return []
        out = []
        dominant = self.dominant_law_code(answer)
        for m in STATUTE_FULL.finditer(answer):
            number = int(m.group(2))
            if self.is_grounded(number, answer, m.start(), m.end(), ctx):
                continue
            code = self.nearby_law_code(answer, m.start(), m.end()) or self.line_law_code(answer, m.start()) \
                or dominant
            if code is not None and self.articles is not None:
                try:
                    if await self.articles.exists(code, number):
                        continue
                except Exception as e:
                    log.debug("article existence check failed: %s", e)
            out.append(UngroundedRef(number, m.group(1), code, m.start(), m.end()))
        return out

    @staticmethod
    def redact(answer: str, refs: list[UngroundedRef]) -> str:
        if not answer or not refs:
            return answer
        out, cursor = [], 0
        for r in refs:
            if r.start < cursor or r.end > len(answer):
                continue
            out.append(answer[cursor:r.start])
            out.append(f"**{r.label} [номер требует проверки — {r.number}]**")
            cursor = r.end
        out.append(answer[cursor:])
        return "".join(out)


@dataclass
class ClaimCheck:
    claim: str
    supported: bool
    entailment: float
    contradiction: float
    source: str | None
    start: int = -1
    end: int = -1

    def as_dict(self) -> dict:
        return {"claim": self.claim, "supported": self.supported, "entailment": round(self.entailment, 3),
                "contradiction": round(self.contradiction, 3),
                "verdict": "подтверждено" if self.supported else "не подтверждено",
                "source": T.truncate(self.source, 300) if self.source else None}


_BOUNDARY = re.compile(r"[.!?]+(?=\s+[A-ZА-ЯЁ«\"(]|\s*$)|\n")


def split_sentences(text: str) -> list[tuple[int, int]]:
    text = text or ""
    bounds, cursor = [], 0
    for m in _BOUNDARY.finditer(text):
        bounds.append((cursor, m.end() if m.group() != "\n" else m.start()))
        cursor = m.end()
    if cursor < len(text):
        bounds.append((cursor, len(text)))
    spans = []
    for s, e in bounds:
        while s < e and text[s] in " \t-*#>•":
            s += 1
        while e > s and text[e - 1] in " \t":
            e -= 1
        if e - s >= MIN_CLAIM_LENGTH:
            spans.append((s, e))
    return spans


class ClaimVerifier:
    def __init__(self, nli: NliModel, ner: NerModel | None, embedder: Embedder | None, threshold: float = 0.8):
        self.nli, self.ner, self.embedder, self.threshold = nli, ner, embedder, threshold

    async def extract_claims(self, answer: str) -> list[tuple[int, int]]:
        spans = split_sentences(answer)
        try:
            ents = [e for e in (await self.ner.extract(answer) if self.ner else []) if e.label in CLAIM_ENTITY_LABELS]
        except Exception as e:
            log.debug("NER failed: %s", e)
            ents = []
        picked = [(s, t) for s, t in spans if any(s <= e.start < t for e in ents)
                  or STATUTE_FULL.search(answer[s:t])]
        return picked[:MAX_CLAIMS]

    async def _best_sources(self, claims: list[str], sources: list[str]) -> list[str]:
        if len(sources) == 1 or self.embedder is None:
            return [sources[0][:1500]] * len(claims)
        try:
            vecs = await self.embedder.embed([*claims, *sources])
            cv, sv = vecs[:len(claims)], vecs[len(claims):]
            out = []
            for v in cv:
                ranked = sorted(range(len(sources)), key=lambda i: -T.cosine(v, sv[i]))[:2]
                out.append("\n".join(sources[i] for i in ranked)[:1500])
            return out
        except Exception as e:
            log.debug("source selection failed: %s", e)
            return [sources[0][:1500]] * len(claims)

    async def verify(self, claims: list[str], sources: list[str]) -> list[ClaimCheck]:
        claims = [c for c in claims if c and c.strip()][:MAX_CLAIMS * 2]
        sources = [s for s in sources if s and s.strip()]
        if not claims:
            return []
        if not sources:
            return [ClaimCheck(c, False, 0.0, 0.0, None) for c in claims]
        premises = await self._best_sources(claims, sources)
        results = await self.nli.predict(list(zip(premises, claims)))
        return [ClaimCheck(c, r.entailment >= self.threshold, r.entailment, r.contradiction, p)
                for c, p, r in zip(claims, premises, results)]

    async def mark(self, answer: str, ctx: DocumentContext | None) -> tuple[str, list[ClaimCheck]]:
        sources = ctx.sources if ctx is not None and not ctx.unavailable else []
        if not sources or not answer:
            return answer, []
        spans = await self.extract_claims(answer)
        if not spans:
            return answer, []
        try:
            checks = await self.verify([answer[s:e] for s, e in spans], sources)
        except Exception as e:
            log.warning("NLI verification failed: %s", e)
            return answer, []
        for chk, (s, e) in zip(checks, spans):
            chk.start, chk.end = s, e
        out = answer
        for chk in sorted((c for c in checks if not c.supported), key=lambda c: -c.end):
            out = out[:chk.end] + UNVERIFIED_MARK + out[chk.end:]
        return out, checks


class AnswerPostProcessor:
    def __init__(self, detector: HallucinationDetector, verifier: ClaimVerifier | None = None):
        self.detector = detector
        self.verifier = verifier

    @staticmethod
    def requires_citations(plan: ContextPlan) -> bool:
        return plan.retrieval in (RetrievalMode.USER_DOCUMENT, RetrievalMode.LAW, RetrievalMode.HYBRID_LEGAL)

    async def process(self, answer: str | None, plan: ContextPlan, ctx: DocumentContext | None) -> str:
        if not answer or not answer.strip():
            return EMPTY_ANSWER
        text = answer.strip()
        if self.requires_citations(plan) and ctx is not None and ctx.has_citations and len(text) < 50:
            return text + SHORT_ANSWER_NOTE
        try:
            refs = await self.detector.ungrounded_refs(text, ctx)
        except Exception as e:
            log.warning("ungrounded reference check failed: %s", e)
            refs = []
        if refs:
            log.warning("Redacting %d ungrounded article reference(s): %s", len(refs), [r.number for r in refs])
            text = self.detector.redact(text, refs)
        if self.verifier is not None:
            text, _ = await self.verifier.mark(text, ctx)
        if refs:
            numbers = ", ".join(str(n) for n in dict.fromkeys(r.number for r in refs))
            text += (f"\n\n> Статьи не найдены в базе законов платформы: **{numbers}**. Перепроверьте по "
                     "официальному источнику (adilet.zan.kz) — модель могла ошибиться в номере.")
        return text
