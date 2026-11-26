import re
from dataclasses import dataclass

from app.analysis.references import line_around

_L = r"[^\W\d_]"


@dataclass(frozen=True)
class KnownCode:
    name: str
    pattern: re.Pattern
    numbers: frozenset[str]
    requisites: str


def _code(name: str, pattern: str, requisites: str, *numbers: str) -> KnownCode:
    return KnownCode(name, re.compile(rf"(?iu){pattern}"), frozenset(numbers), requisites)


KNOWN_CODES: list[KnownCode] = [
    _code("ГПК РК", rf"гражданск{_L}*\s+процессуальн{_L}*\s+кодекс{_L}*|\bГПК\b", "от 31.10.2015 № 377-V", "377-V"),
    _code("УПК РК", rf"уголовно-процессуальн{_L}*\s+кодекс{_L}*|\bУПК\b", "от 04.07.2014 № 231-V", "231-V"),
    _code("ТК РК", rf"трудов{_L}*\s+кодекс{_L}*|\bТК\s+РК\b", "от 23.11.2015 № 414-V", "414-V"),
    _code("ГК РК", rf"гражданск{_L}*\s+кодекс{_L}*|\bГК\s+РК\b",
          "Общая часть от 27.12.1994 № 268-XIII, Особенная часть от 01.07.1999 № 409-I", "268-XIII", "409-I"),
    _code("УК РК", rf"уголовн{_L}*\s+кодекс{_L}*|\bУК\s+РК\b", "от 03.07.2014 № 226-V", "226-V"),
    _code("КоАП РК", rf"кодекс{_L}*\s+(?:республики\s+казахстан\s+)?об\s+административных\s+правонарушениях|\bКоАП\b",
          "от 05.07.2014 № 235-V", "235-V"),
    _code("Предпринимательский кодекс РК", rf"предпринимательск{_L}*\s+кодекс{_L}*", "от 29.10.2015 № 375-V", "375-V"),
    _code("Социальный кодекс РК", rf"социальн{_L}*\s+кодекс{_L}*", "от 20.04.2023 № 224-VII", "224-VII"),
    _code("Земельный кодекс РК", rf"земельн{_L}*\s+кодекс{_L}*", "от 20.06.2003 № 442-II", "442-II"),
]
SUPERSEDED = {"ТК РК": {"251-III"}, "ГПК РК": {"411-I"}, "УК РК": {"167-I"}, "УПК РК": {"206-I"}}

_NUMBER = re.compile(r"(?iu)(?:№|N)\s*(\d{1,4})\s*-\s*([IVXLІ]+)(?!" + _L + ")")
_BOUNDARY = re.compile(r"[;\n]|\.\s+[А-ЯЁA-Z]")
_OTHER_ACT = re.compile(rf"(?iu)закон{_L}*|кодекс{_L}*|постановлени{_L}*|приказ{_L}*|указ{_L}*|решени{_L}*")
LOOKAHEAD = 90


@dataclass(frozen=True)
class RequisiteIssue:
    code: str
    cited_number: str
    requisites: str
    clause: str
    correct: str = ""

    @property
    def explanation(self) -> str:
        return (f"В документе указан {self.code} № {self.cited_number}, а правильные реквизиты — "
                f"{self.requisites}.")


def _norm(num: str, roman: str) -> str:
    return f"{num}-{roman.upper().replace('І', 'I')}"


def check_requisites(text: str | None) -> list[RequisiteIssue]:
    if not text:
        return []
    out: dict[tuple[str, str], RequisiteIssue] = {}
    taken: list[tuple[int, int]] = []
    for code in KNOWN_CODES:
        for m in code.pattern.finditer(text):
            if any(s <= m.start() < e for s, e in taken):
                continue
            taken.append((m.start(), m.end()))
            window = text[m.end(): m.end() + LOOKAHEAD]
            nm = _NUMBER.search(window)
            between = window[:nm.start()] if nm else ""
            if not nm or _BOUNDARY.search(between) or _OTHER_ACT.search(between):
                continue
            number = _norm(nm.group(1), nm.group(2))
            if number in code.numbers or number in SUPERSEDED.get(code.name, set()):
                continue
            out.setdefault((code.name, number),
                           RequisiteIssue(code.name, number, code.requisites, line_around(text, m.start()),
                                          next(iter(code.numbers)) if len(code.numbers) == 1 else ""))
    return list(out.values())
