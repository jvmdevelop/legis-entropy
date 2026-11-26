import asyncio
import re
from dataclasses import dataclass, field
from typing import Protocol

LABELS = ["закон", "кодекс", "статья", "дата", "сумма", "организация", "срок"]


@dataclass
class Entity:
    label: str
    text: str
    start: int
    end: int
    score: float = 1.0
    attrs: dict = field(default_factory=dict)


@dataclass
class ArticleRef:
    article: str
    paragraph: str | None
    law_hint: str | None
    law_code: str | None
    start: int
    end: int
    raw: str


_ART = re.compile(
    r"(?iu)(?:(?:п(?:ункт[а-я]*|\.)?\s*(?P<par>\d+(?:\.\d+)*)\s*,?\s*)?"
    r"(?:ст(?:ать[яиеёюй]*|\.)?)\s*(?P<num>\d+(?:[-.]\d+)?))")
_ABBR = {
    "тк рк": "414-V", "трудового кодекса": "414-V", "трудовой кодекс": "414-V", "трудовому кодексу": "414-V",
    "гк рк": "ГК РК", "гражданского кодекса": "ГК РК", "гражданский кодекс": "ГК РК",
    "налогового кодекса": "НК РК", "нк рк": "НК РК", "коап": "КоАП РК", "ук рк": "УК РК",
    "гк рф": "ГК РФ", "тк рф": "ТК РФ",
}
_LAW_NUM = re.compile(r"(?iu)№\s*(?P<code>\d{1,4}-[IVXL]+)|(?P<code2>\b\d{1,4}-[IVXL]{1,5}\b)")
_LAW_NAME = re.compile(r"(?iu)(закон[а-я]*\s+(?:республики\s+казахстан|рк|рф)?\s*«[^»]{3,200}»"
                       r"|(?:трудов|гражданск|налогов|уголовн|земельн|бюджетн|социальн|предпринимательск)"
                       r"[а-я]*\s+кодекс[а-я]*(?:\s+(?:рк|рф|республики\s+казахстан))?"
                       r"|\b(?:тк|гк|нк|ук|коап|апк|гпк)\s+(?:рк|рф)\b)")
_DATE = re.compile(r"(?iu)\b\d{1,2}[./]\d{1,2}[./]\d{2,4}\b|\b\d{1,2}\s+(?:января|февраля|марта|апреля|мая|июня|"
                   r"июля|августа|сентября|октября|ноября|декабря)\s+\d{4}\s*(?:г(?:ода|\.)?)?")
_SUM = re.compile(r"(?iu)\b\d[\d\s]*(?:[.,]\d+)?\s*(?:\([^()\d]{3,90}\)\s*)?(?:тенге|тг|₸|руб(?:лей|\.)?|₽|мрп|usd|\$|долл)")
LOOKAHEAD = 120


def resolve_law(hint: str | None) -> str | None:
    if not hint:
        return None
    h = hint.lower()
    m = _LAW_NUM.search(hint)
    if m:
        return m.group("code") or m.group("code2")
    for k, v in _ABBR.items():
        if k in h:
            return v
    return None


def extract_article_refs(text: str) -> list[ArticleRef]:
    refs: list[ArticleRef] = []
    laws = [(m.start(), m.end(), m.group(0)) for m in _LAW_NAME.finditer(text)]
    laws += [(m.start(), m.end(), m.group(0)) for m in _LAW_NUM.finditer(text)]
    laws.sort()
    for m in _ART.finditer(text):
        hint = None
        after = [l for l in laws if m.end() <= l[0] <= m.end() + LOOKAHEAD]
        before = [l for l in laws if m.start() - LOOKAHEAD <= l[1] <= m.start()]
        if after:
            hint = after[0][2]
        elif before:
            hint = before[-1][2]
        refs.append(ArticleRef(m.group("num"), m.group("par"), hint, resolve_law(hint),
                               m.start(), m.end(), text[m.start(): m.end() + (len(hint) + 5 if hint and after else 0)]))
    return refs


class NerModel(Protocol):
    name: str

    async def extract(self, text: str) -> list[Entity]: ...


class RegexNer:
    name = "regex"

    async def extract(self, text: str) -> list[Entity]:
        out = [Entity("law", m.group(0), m.start(), m.end(), attrs={"code": resolve_law(m.group(0))})
               for m in _LAW_NAME.finditer(text)]
        out += [Entity("law", m.group(0), m.start(), m.end(), attrs={"code": m.group("code") or m.group("code2")})
                for m in _LAW_NUM.finditer(text)]
        out += [Entity("article", r.raw, r.start, r.end,
                       attrs={"number": r.article, "paragraph": r.paragraph, "lawCode": r.law_code, "lawHint": r.law_hint})
                for r in extract_article_refs(text)]
        out += [Entity("date", m.group(0), m.start(), m.end()) for m in _DATE.finditer(text)]
        out += [Entity("sum", m.group(0).strip(), m.start(), m.end()) for m in _SUM.finditer(text)]
        return sorted(out, key=lambda e: e.start)


_ROLE = re.compile(r"(?iu)^(?:сторон|работодател|работник|заказчик|исполнител|арендатор|арендодател|подрядчик|"
                   r"поставщик|покупател|продав|кредитор|должник|истец|ответчик|наймодател|нанимател)[^\W\d_]*$")
_HAS_NUMBER = re.compile(r"(?iu)\d|\b(?:од(?:ин|на|но|ного|ной)|дв[аеу]х?|тр(?:и|[её]х)|четыр|пят|шест|сем|восем|"
                         r"девят|десят|двадцат|тридцат|сорок|ста?\b|полугод|недел|месяц|год)")


def plausible(label: str, text: str) -> bool:
    t = text.strip()
    if label == "org":
        return not _ROLE.match(t)
    if label == "sum":
        return bool(re.search(r"\d", t))
    if label == "term":
        return bool(_HAS_NUMBER.search(t)) and not _DATE.fullmatch(t)
    return True


class GlinerNer:
    name = "gliner"
    _MAP = {"закон": "law", "кодекс": "law", "статья": "article", "дата": "date", "сумма": "sum",
            "организация": "org", "срок": "term"}
    CHUNK, STRIDE, MAX_CHUNKS = 1500, 1400, 40

    def __init__(self, model: str, device: str, threshold: float = 0.4):
        from gliner import GLiNER
        self._model = GLiNER.from_pretrained(model).to(device)
        self.threshold = threshold

    async def extract(self, text: str) -> list[Entity]:
        chunks = [(i, text[i:i + self.CHUNK]) for i in range(0, len(text), self.STRIDE)][: self.MAX_CHUNKS]

        def run() -> list[Entity]:
            res = []
            for off, ch in chunks:
                for e in self._model.predict_entities(ch, LABELS, threshold=self.threshold):
                    label = self._MAP.get(e["label"], e["label"])
                    if not plausible(label, e["text"]):
                        continue
                    res.append(Entity(label, e["text"], off + e["start"], off + e["end"], float(e["score"]),
                                      attrs={"code": resolve_law(e["text"])} if label == "law" else {}))
            return res

        return await asyncio.to_thread(run)


class CompositeNer:
    def __init__(self, primary: NerModel, secondary: NerModel):
        self.primary, self.secondary = primary, secondary
        self.name = f"{primary.name}+{secondary.name}"

    async def extract(self, text: str) -> list[Entity]:
        base = await self.primary.extract(text)
        spans = [(e.start, e.end) for e in base]
        extra = [e for e in await self.secondary.extract(text) if not any(s <= e.start < t for s, t in spans)]
        return sorted(base + extra, key=lambda e: e.start)
