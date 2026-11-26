import re
from dataclasses import dataclass
from typing import Callable

from app.analysis.labor import Violation, last_number

GK = "ГК РК"
_L = r"[^\W\d_]"
_CLAUSE_NUMBER = re.compile(r"^\s*(\d+(?:\.\d+)+)")
_CITED = re.compile(r"(?iu)(?:стать[^\W\d_]*|ст\.)\s*(\d+)")
_LIMITATION = re.compile(rf"(?iu)исков{_L}*\s+давност")
_UNIT = re.compile(rf"(?iu)(год|лет|месяц|дн[еяи]|день)")
_PENALTY = re.compile(rf"(?iu)неустойк|штраф|пен[яиюь]\b")
_NO_REDUCTION = re.compile(rf"(?iu)не\s+(?:подлеж{_L}*|может\s+быть)\s+уменьш|уменьшени{_L}*\s+не\s+подлеж|"
                           rf"отказыва{_L}*\s+от\s+права\s+(?:на\s+)?(?:её\s+|ее\s+)?уменьш")
_EXECUTOR_EXIT = re.compile(rf"(?iu)исполнител{_L}*[^.]{{0,80}}отказ{_L}*[^.]{{0,80}}без\s+(?:возмещ|компенсац|выплат|уплат)")
_CUSTOMER_LOCK = re.compile(rf"(?iu)заказчик{_L}*[^.]{{0,40}}(?:не\s+вправе|не\s+может|лишается\s+права)[^.]{{0,40}}отказ")


@dataclass(frozen=True)
class CivilRule:
    article: str
    applies: Callable[[str], str | None]


def _limitation(line: str) -> str | None:
    if not _LIMITATION.search(line):
        return None
    unit = _UNIT.search(line)
    if not unit:
        return None
    value = last_number(line[max(0, unit.start() - 30): unit.start()])
    if value is None:
        return None
    if unit.group(1).lower() in ("год", "лет") and value == 3:
        return None
    return (f"Договор устанавливает свой срок исковой давности ({value} {unit.group(1)}…), но сроки исковой давности "
            "предусматриваются законом и не могут быть изменены соглашением сторон (п. 2 ст. 177 ГК РК; общий срок — "
            "три года, ст. 178).")


def _no_penalty_reduction(line: str) -> str | None:
    if _PENALTY.search(line) and _NO_REDUCTION.search(line):
        return ("Условие о том, что неустойка не подлежит уменьшению, противоречит ст. 297 ГК РК: суд по требованию "
                "должника вправе уменьшить чрезмерно высокую неустойку.")
    return None


def _services_exit(line: str) -> str | None:
    if _EXECUTOR_EXIT.search(line):
        return ("Исполнитель вправе отказаться от договора возмездного оказания услуг лишь при условии полного "
                "возмещения заказчику убытков (п. 2 ст. 686 ГК РК).")
    if _CUSTOMER_LOCK.search(line):
        return ("Заказчик вправе отказаться от договора возмездного оказания услуг при условии оплаты исполнителю "
                "фактически понесённых расходов (п. 1 ст. 686 ГК РК); лишить его этого права договором нельзя.")
    return None


CIVIL_RULES = [CivilRule("177", _limitation), CivilRule("297", _no_penalty_reduction), CivilRule("686", _services_exit)]


def civil_check(text: str | None) -> list[Violation]:
    found, seen = [], set()
    for line in (text or "").splitlines():
        for rule in CIVIL_RULES:
            why = rule.applies(line)
            if not why:
                continue
            clause, cited = _CLAUSE_NUMBER.search(line), _CITED.search(line)
            key = (clause.group(1) if clause else line[:40], rule.article)
            if key in seen:
                continue
            seen.add(key)
            found.append(Violation(clause.group(1) if clause else "", cited.group(1) if cited else "",
                                   rule.article, why, GK))
    return found
