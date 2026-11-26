import logging
import re
from dataclasses import dataclass

from app.clients.ports import LawPort

log = logging.getLogger(__name__)

COUNTRY = "RK"
MAX_TEXT = 40000
MATCH_SHARE = 0.8
STEM = 4
_ENTRY = re.compile(r"(?s)(?:^|\s)(\d{1,3})\)\s*([^–—:;\n]{2,90}?)\s+[–—-]\s+(.+?)(?=;\s*\d{1,3}\)\s|\.\s*\d{1,2}\.\s|\Z)")
_WORD = re.compile(r"[^\W\d_]{4,}", re.U)

CODE_HINTS: list[tuple[str, re.Pattern]] = [(code, re.compile(p, re.I | re.U)) for code, p in [
    ("ТК РК", r"трудов|работодател|работник|\bотпуск|заработн|\bтк\s+рк"),
    ("НК РК", r"налог|\bндс\b|\bипн\b|\bнк\s+рк"),
    ("Земельный кодекс РК", r"земельн|земельн\w*\s+участ"),
    ("Кодекс «О браке (супружестве) и семье»", r"\bбрак|супруг|алимент|опек"),
    ("Экологический кодекс РК", r"экологи|выброс|отход"),
]]


@dataclass(frozen=True)
class Term:
    term: str
    definition: str
    code: str
    article: str


def parse_definitions(text: str, code: str, article: str) -> list[Term]:
    out = []
    for m in _ENTRY.finditer(text or ""):
        term = re.sub(r"\s+", " ", m.group(2)).strip(" ,")
        definition = re.sub(r"\s+", " ", m.group(3)).strip()
        if 2 <= len(term) <= 90 and len(definition) >= 10:
            out.append(Term(term, definition[:600], code, article))
    return out


def _stems(text: str) -> set[str]:
    return {w.lower().replace("ё", "е")[:STEM] for w in _WORD.findall(text or "")}


def codes_for(text: str) -> list[str]:
    return [code for code, p in CODE_HINTS if p.search(text or "")]


class GlossaryService:
    def __init__(self, laws: LawPort):
        self.laws = laws
        self._cache: dict[str, list[Term]] = {}

    async def terms(self, code: str) -> list[Term]:
        if code in self._cache:
            return self._cache[code]
        terms: list[Term] = []
        try:
            titles = await self.laws.article_titles(code, COUNTRY)
            number = next((str(t.get("articleNumber")) for t in titles
                           if (t.get("articleTitle") or "").lower().startswith("основные понятия")), None)
            if number:
                found = await self.laws.lookup_articles(code, [number], COUNTRY, MAX_TEXT)
                if found:
                    terms = parse_definitions(found[0].get("excerpt") or "", code, number)
        except Exception as e:
            log.warning("glossary for %s unavailable: %s", code, e)
            return []
        self._cache[code] = terms
        return terms

    async def relevant(self, text: str, codes: list[str] | None = None, limit: int = 8) -> list[Term]:
        stems = _stems(text)
        if not stems:
            return []
        picked: list[tuple[int, Term]] = []
        for code in codes if codes is not None else codes_for(text):
            for t in await self.terms(code):
                words = _stems(t.term)
                if words and len(words & stems) / len(words) >= MATCH_SHARE:
                    picked.append((len(words & stems), t))
        picked.sort(key=lambda x: -x[0])
        seen, out = set(), []
        for _, t in picked:
            if t.term.lower() not in seen:
                seen.add(t.term.lower())
                out.append(t)
        return out[:limit]


def render(terms: list[Term]) -> str:
    if not terms:
        return ""
    lines = ["Определения терминов из законодательства РК (используй именно эти значения):"]
    lines += [f"- {t.term} — {t.definition} ({t.code}, ст. {t.article})" for t in terms]
    return "\n".join(lines)
