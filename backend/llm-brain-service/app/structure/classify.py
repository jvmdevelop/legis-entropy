import logging
import re

from pydantic import BaseModel

from app.llm.client import Completer
from app.structure.parser import Clause, ClauseType

log = logging.getLogger(__name__)

_L = r"[^\W\d_]"

TYPES: dict[str, str] = {
    "PAYMENT": "оплата и цена",
    "TERM": "сроки",
    "TERMINATION": "расторжение и односторонний отказ",
    "LIABILITY": "ответственность и неустойка",
    "LIABILITY_LIMIT": "ограничение ответственности",
    "CONFIDENTIALITY": "конфиденциальность",
    "DISPUTES": "споры и подсудность",
    "FORCE_MAJEURE": "непреодолимая сила",
    "AMENDMENT": "изменение договора",
    "WARRANTY": "гарантии и качество",
    "PERSONAL_DATA": "персональные данные",
    "SUBJECT": "предмет договора",
    "WORK_CONDITIONS": "условия труда и отдыха",
    "OTHER": "прочее",
}

_RULES: list[tuple[str, re.Pattern]] = [(code, re.compile(rf"(?iu){p}")) for code, p in [
    ("PAYMENT", rf"оплат|стоимост|цен[аыу]\b|вознагражден|оклад|заработн{_L}*\s+плат|арендн{_L}*\s+плат|тенге|аванс"),
    ("TERM", rf"\bсрок{_L}*\b|не\s+позднее|в\s+течение\s+\d|дата\s+начала|вступает\s+в\s+силу"),
    ("TERMINATION", rf"расторг|расторжени|отказ{_L}*\s+от\s+(?:исполнения|договора)|прекращени{_L}*\s+договор"),
    ("LIABILITY", rf"неустойк|штраф|\bпен[яиюь]\b|ответственност|возмещ{_L}*\s+убытк"),
    ("LIABILITY_LIMIT", rf"не\s+нес[её]т\s+ответственност|ответственност{_L}*\s+ограничива|только\s+(?:реальн{_L}*|прям{_L}*)\s+ущерб|не\s+более\s+\d"),
    ("CONFIDENTIALITY", rf"конфиденциальн|коммерческ{_L}*\s+тайн|не\s+разглаш"),
    ("DISPUTES", rf"\bспор{_L}*|подсудн|\bв\s+суд|судебн{_L}*\s+порядк|претензи|арбитраж|исков{_L}*\s+давност"),
    ("FORCE_MAJEURE", rf"непреодолим{_L}*\s+сил|форс-?мажор"),
    ("AMENDMENT", rf"изменени{_L}*\s+(?:и\s+дополнени{_L}*\s+)?(?:настоящего\s+)?договор|дополнительн{_L}*\s+соглашени"),
    ("WARRANTY", rf"гарант|качеств"),
    ("PERSONAL_DATA", rf"персональн{_L}*\s+данн"),
    ("SUBJECT", rf"предмет|обязуется\s+(?:выполнить|оказать|передать|поставить)|передает\s+.*во\s+временное|принимается\s+на\s+работу"),
    ("WORK_CONDITIONS", rf"рабоч{_L}*\s+(?:время|недел|день)|отпуск|испытательн|перерыв|выходн"),
]]

ROLES: dict[str, str] = {
    "работодател": "Работодатель", "работник": "Работник", "арендодател": "Арендодатель", "арендатор": "Арендатор",
    "наймодател": "Наймодатель", "нанимател": "Наниматель", "заказчик": "Заказчик", "исполнител": "Исполнитель",
    "подрядчик": "Подрядчик", "поставщик": "Поставщик", "покупател": "Покупатель", "продав": "Продавец",
    "займодат": "Займодатель", "заемщик": "Заемщик", "сторон": "Стороны",
}
_ROLE = re.compile(rf"(?iu)\b({'|'.join(ROLES)}){_L}*")
_PROHIBITION = re.compile(rf"(?iu)\bне\s+вправе|\bзапрещ|\bне\s+допускается|\bне\s+может\b|\bне\s+имеет\s+права")
_RIGHT = re.compile(rf"(?iu)\bвправе\b|имеет\s+право|\bможет\b")
_OBLIGATION = re.compile(rf"(?iu)\bобязан{_L}*|\bобязуется|\bобязуются|\bдолжен|\bдолжны|\bнес[её]т\b|\bуплачивает|\bпроизводит")


def _norm_role(word: str) -> str:
    return ROLES.get(word.lower(), word)


def modality(text: str) -> tuple[str | None, str | None]:
    for kind, pattern in (("PROHIBITION", _PROHIBITION), ("RIGHT", _RIGHT), ("OBLIGATION", _OBLIGATION)):
        m = pattern.search(text)
        if m:
            before = list(_ROLE.finditer(text[: m.start()]))
            after = _ROLE.search(text, m.end())
            party = before[-1].group(1) if before else (after.group(1) if after else None)
            return kind, _norm_role(party) if party else None
    roles = list(_ROLE.finditer(text))
    return None, _norm_role(roles[0].group(1)) if roles else None


def classify_rules(clause: Clause) -> None:
    body = re.sub(r"^\s*\d+(?:\.\d+)+\.?\s*", "", clause.text).replace("ё", "е").replace("Ё", "Е")
    codes = [code for code, p in _RULES if p.search(body)]
    clause.types = [ClauseType(c, TYPES[c]) for c in codes]
    clause.modality, clause.party = modality(body)


class _Item(BaseModel):
    number: str
    types: list[str] = []


class _Items(BaseModel):
    items: list[_Item] = []


SYSTEM = ("Ты — юрист РК. Классифицируй пункты договора по типам условий. Допустимые коды: "
          + ", ".join(f"{k} ({v})" for k, v in TYPES.items())
          + ". У пункта может быть несколько типов. Верни JSON {items:[{number, types:[код,...]}]}.")
MAX_LLM = 40


async def classify(clauses: list[Clause], llm: Completer | None = None) -> list[Clause]:
    for c in clauses:
        classify_rules(c)
    todo = [c for c in clauses if not c.types][:MAX_LLM]
    if todo and llm is not None:
        body = "\n".join(f"[{c.number}] {c.text[:400]}" for c in todo)
        try:
            out = await llm.structured(SYSTEM, body, _Items)
        except Exception as e:
            log.warning("clause classification LLM failed: %s", e)
            out = None
        by_number = {c.number: c for c in todo}
        for it in (out.items if out else []):
            c = by_number.get(it.number)
            if c is not None:
                c.types = [ClauseType(t, TYPES[t], "llm") for t in it.types if t in TYPES]
    for c in clauses:
        if not c.types:
            c.types = [ClauseType("OTHER", TYPES["OTHER"])]
    return clauses
