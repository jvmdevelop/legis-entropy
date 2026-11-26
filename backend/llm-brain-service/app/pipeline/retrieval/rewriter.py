import logging
import re

from pydantic import BaseModel, Field

from app.pipeline.text import contains_any, normalize
from app.llm.client import Completer

log = logging.getLogger(__name__)

MIN_SUB_QUESTION_LENGTH = 15
MAX_SUB_QUESTIONS = 12
MAX_QUERY_LENGTH = 500

CONVERSATIONAL_FILLER = [
    "привет", "пожалуйста", "спасибо", "пока", "давай", "окей", "скажи мне", "расскажи", "расскажи мне",
    "объясни", "объясни мне", "что такое", "как это", "помоги", "помогите", "дай мне", "можешь ли",
    "сможешь ли", "можно ли", "может ли",
]
LAW_TERMS = [
    "закон", "законы", "законодательство", "нпа", "нормативный", "нормативные", "статья", "статьи", "норма",
    "нормы", "кодекс", "гк", "ук", "коап", "тк", "налоговый кодекс", "гражданский кодекс", "трудовой кодекс",
    "правило", "правила", "постановление", "приказ", "регламент", "судебная практика", "практика суда",
    "правовая позиция", "санкция", "штраф", "ответственность", "требования закона", "правомерно", "законно",
    "незаконно", "соответствует закону", "развод", "алименты", "брак", "ребенок", "дети", "опека", "аренда",
    "недвижимость", "квартира", "земля", "ипотека", "договор", "преступление", "компания",
]
COMPARISON_TERMS = ["сравни", "сравнить", "сопоставь", "сопоставить", "соответствует", "противоречит",
                    "противоречие", "расхождение", "коллизия", "отличие"]
QUESTION_WORDS = ("что ", "как ", "где ", "когда ", "кто ", "почему ", "зачем ", "сколько ")
STATUTE = re.compile(r"(?i)(?:ст\.|статья|статьи)\s*\d+")
ABBREVIATION = re.compile(r"\b(?:ГК|ТК|СК|УК|НК|ЖК|ЗК|РК|РФ|КОАП)\b")


def _remove_filler(n: str) -> str:
    for f in CONVERSATIONAL_FILLER:
        n = n.replace(f + " ", "").replace(" " + f, "")
    for w in QUESTION_WORDS:
        if n.startswith(w):
            n = n[len(w):].strip()
            break
    return n.strip()


def _signals(n: str) -> int:
    return (sum(t in n for t in LAW_TERMS) + sum(t in n for t in COMPARISON_TERMS)
            + bool(STATUTE.search(n)) + bool(ABBREVIATION.search(n)))


def _is_signal(word: str) -> bool:
    low = normalize(word)
    return contains_any(low, LAW_TERMS) or contains_any(low, COMPARISON_TERMS) or bool(STATUTE.search(low)) \
        or bool(ABBREVIATION.search(word))


def _around_terms(cleaned: str) -> str:
    words = cleaned.split()
    parts = []
    for i, w in enumerate(words):
        if _is_signal(w):
            parts.append(" ".join(words[max(0, i - 3): min(len(words), i + 4)]))
    return " ".join(parts).strip()


def _truncate(text: str, n: int = MAX_QUERY_LENGTH) -> str:
    if len(text) <= n:
        return text
    cut = text[:n]
    sp = cut.rfind(" ")
    return cut[:sp] if sp > n // 2 else cut


def rewrite(raw: str | None) -> str:
    if raw is None or not raw.strip():
        return raw or ""
    cleaned = _remove_filler(normalize(raw))
    if _signals(cleaned) < 2:
        cleaned = _around_terms(cleaned) or cleaned
    return _truncate(cleaned)


def rewrite_all(raw: str | None) -> list[str]:
    if raw is None or not raw.strip():
        return [raw or ""]
    queries: list[str] = []
    for part in raw.split("?"):
        part = part.strip()
        if len(part) < MIN_SUB_QUESTION_LENGTH:
            continue
        q = rewrite(part)
        if q and q.strip() and q not in queries:
            queries.append(q)
        if len(queries) >= MAX_SUB_QUESTIONS:
            break
    return queries if len(queries) > 1 else [rewrite(raw)]


class _Decomposition(BaseModel):
    sub_questions: list[str] = Field(default_factory=list, alias="subQuestions")
    hypothetical: str = ""

    model_config = {"populate_by_name": True}


DECOMPOSE_SYSTEM = (
    "Ты — модуль переписывания поисковых запросов для базы нормативных актов Республики Казахстан. "
    "Разбей вопрос пользователя на самостоятельные юридические подвопросы (не более 12), каждый — "
    "короткий поисковый запрос на русском без вежливых слов, с юридическими терминами. "
    "Если вопрос простой — верни один подвопрос. В поле hypothetical напиши 2-3 предложения, как мог бы "
    "звучать фрагмент закона РК, отвечающий на вопрос (HyDE), без выдуманных номеров статей.")


class QueryRewriter:
    def __init__(self, llm: Completer | None = None, min_llm_length: int = 40):
        self.llm = llm
        self.min_llm_length = min_llm_length

    async def rewrite(self, message: str | None) -> list[str]:
        queries = rewrite_all(message)
        if self.llm is None or len((message or "").strip()) < self.min_llm_length:
            return queries
        try:
            out = await self.llm.structured(DECOMPOSE_SYSTEM, (message or "")[:4000], _Decomposition)
        except Exception as e:
            log.debug("LLM query decomposition failed: %s", e)
            out = None
        if out is None:
            return queries
        extra = [*out.sub_questions, out.hypothetical]
        merged = list(queries)
        for q in extra:
            q = _truncate(re.sub(r"\s+", " ", (q or "")).strip())
            if len(q) >= MIN_SUB_QUESTION_LENGTH and q not in merged:
                merged.append(q)
        return merged[:MAX_SUB_QUESTIONS]
