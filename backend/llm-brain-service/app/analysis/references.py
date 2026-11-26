import logging
import re
from dataclasses import dataclass

from app.ml.ner import NerModel

log = logging.getLogger(__name__)

_ARTICLE = re.compile(r"(?iu)(?<![^\W\d_])(?:стать[а-яё]*|ст\.)\s*(\d+(?:-\d+)?(?:\s*(?:,|и)\s*\d+(?:-\d+)?)*)")
_NUMBER = re.compile(r"\d+(?:-\d+)?")
_NOT_A_CODE = re.compile(r"(?iu)настоящ|(?<![^\W\d_])закон")
LOOKAHEAD = 120
_L = r"[^\W\d_]"


def _rule(code: str, full: str, abbr: str) -> tuple[str, re.Pattern, re.Pattern]:
    return code, re.compile("(?iu)" + full.replace(r"\p{L}", _L)), re.compile(rf"(?<!{_L}){abbr}(?!{_L})")


CODES = [
    _rule("ГПК РК", r"гражданск\p{L}*\s+процессуальн\p{L}*\s+кодекс", "ГПК"),
    _rule("УПК РК", r"уголовно[\s-]*процессуальн\p{L}*\s+кодекс", "УПК"),
    _rule("ТК РК", r"трудов\p{L}*\s+кодекс", "ТК"),
    _rule("ГК РК", r"гражданск\p{L}*\s+кодекс", "ГК"),
    _rule("НК РК", r"налогов\p{L}*\s+кодекс", "НК"),
    _rule("УК РК", r"уголовн\p{L}*\s+кодекс", "УК"),
    _rule("КоАП РК", r"кодекс\p{L}*[^.]{0,30}административных\s+правонарушени", "КоАП"),
    _rule("Земельный кодекс РК", r"земельн\p{L}*\s+кодекс", "ЗК"),
    _rule("ПК РК", r"предпринимательск\p{L}*\s+кодекс", "ПК"),
    _rule("Экологический кодекс РК", r"экологическ\p{L}*\s+кодекс", "ЭК"),
    _rule("Кодекс «О браке (супружестве) и семье»", r"кодекс\p{L}*[^.]{0,15}о\s+браке", "КоБС"),
]


@dataclass(frozen=True)
class ArticleReference:
    law_code: str
    article_number: str
    clause: str
    position: int


def line_around(text: str, pos: int) -> str:
    start = text.rfind("\n", 0, pos) + 1
    end = text.find("\n", pos)
    line = text[start: end if end >= 0 else len(text)].strip()
    return line if len(line) <= 400 else line[:400] + "…"


def resolve_code(window: str) -> str | None:
    window = window.split("\n", 1)[0]
    best, best_pos = None, 1 << 30
    for code, full, abbr in CODES:
        for p in (full, abbr):
            m = p.search(window)
            if m and m.start() < best_pos:
                best, best_pos = code, m.start()
    if best is None:
        return None
    other = _NOT_A_CODE.search(window)
    return None if other and other.start() < best_pos else best


def canonical_code(law_code: str | None) -> str | None:
    return resolve_code(law_code) if law_code and law_code.strip() else None


def extract_references(text: str | None) -> list[ArticleReference]:
    if not text or not text.strip():
        return []
    refs = []
    for m in _ARTICLE.finditer(text):
        code = resolve_code(text[m.end(): m.end() + LOOKAHEAD])
        if code is None:
            continue
        clause = line_around(text, m.start())
        refs += [ArticleReference(code, n.group(), clause, m.start()) for n in _NUMBER.finditer(m.group(1))]
    return refs


async def extract_with_ner(text: str, ner: NerModel | None) -> list[ArticleReference]:
    refs = extract_references(text)
    if ner is None:
        return refs
    try:
        entities = await ner.extract(text)
    except Exception as e:
        log.warning("NER failed, regex references only: %s", e)
        return refs
    seen = {(r.law_code, r.article_number, r.clause) for r in refs}
    for e in entities:
        if e.label != "article" or not e.attrs.get("number"):
            continue
        code = canonical_code(e.attrs.get("lawHint")) or canonical_code(e.attrs.get("lawCode"))
        if code is None:
            continue
        ref = ArticleReference(code, str(e.attrs["number"]), line_around(text, e.start), e.start)
        if (ref.law_code, ref.article_number, ref.clause) not in seen:
            seen.add((ref.law_code, ref.article_number, ref.clause))
            refs.append(ref)
    return sorted(refs, key=lambda r: r.position)
