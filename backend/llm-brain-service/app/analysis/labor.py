import logging
import re
from dataclasses import dataclass

from pydantic import BaseModel, field_validator
from app.llm.client import Completer

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class Violation:
    clause: str
    cited_article: str
    correct_article: str
    explanation: str
    law_code: str = "ТК РК"


@dataclass(frozen=True)
class Rule:
    kind: str
    line: re.Pattern
    unit: re.Pattern
    minimum: bool
    limit: int
    article: str
    what: str

    def broken(self, value: int) -> bool:
        return value < self.limit if self.minimum else value > self.limit


RULES = [
    Rule("vacation_days", re.compile(r"(?iu)отпуск"), re.compile(r"(?iu)календарн"), True, 24, "88",
         "основной оплачиваемый ежегодный отпуск по ст. 88 ТК РК — не менее 24 календарных дней"),
    Rule("probation_months", re.compile(r"(?iu)испытательн"), re.compile(r"(?iu)месяц"), False, 3, "36",
         "испытательный срок по ст. 36 ТК РК — не более трёх месяцев (для руководителей — до шести)"),
    Rule("weekly_hours", re.compile(r"(?iu)рабоч[^\W\d_]*\s+(?:недел|времен)"), re.compile(r"(?iu)час"), False, 40, "68",
         "нормальная продолжительность рабочего времени по ст. 68 ТК РК — не более 40 часов в неделю"),
]
_BY_KIND = {r.kind: r for r in RULES}
_CLAUSE_NUMBER = re.compile(r"^\s*(\d+(?:\.\d+)+)")
_CITED = re.compile(r"(?iu)(?:стать[^\W\d_]*|ст\.)\s*(\d+)")

_TENS = [("двадцат", 20), ("тридцат", 30), ("сорок", 40), ("пятьдесят", 50)]
_TEENS = [("одиннадцат", 11), ("двенадцат", 12), ("тринадцат", 13), ("четырнадцат", 14), ("пятнадцат", 15),
          ("шестнадцат", 16), ("семнадцат", 17), ("восемнадцат", 18), ("девятнадцат", 19), ("десят", 10)]
_UNITS = {w: n for n, ws in [(1, "один одна одно одного одной одному одним одну"), (2, "два две двух двум двумя"),
                             (3, "три трех трем тремя"), (4, "четыре четырех четырем"), (5, "пять пяти"),
                             (6, "шесть шести"), (7, "семь семи"), (8, "восемь восьми"), (9, "девять девяти")]
          for w in ws.split()}
_TOKEN = re.compile(r"\d+|[^\W\d_]+")


def _word_value(token: str) -> int | None:
    for prefix, v in _TENS + _TEENS:
        if token.startswith(prefix):
            return v
    return _UNITS.get(token)


def last_number(window: str) -> int | None:
    last, run = None, -1
    for token in _TOKEN.findall(window.lower().replace("ё", "е")):
        if token[0].isdigit():
            last, run = int(token), -1
            continue
        w = _word_value(token)
        if w is None:
            run = -1
            continue
        run = run + w if run >= 20 and run % 10 == 0 and w < 10 else w
        last = run
    return last


def _violation(rule: Rule, value: int, clause: str, cited: str) -> Violation:
    return Violation(clause, cited, rule.article, f"В договоре указано {value}, а {rule.what}.")


def _applies(text: str | None) -> bool:
    return bool(text) and "трудов" in text.lower()


def regex_check(text: str | None) -> list[Violation]:
    if not _applies(text):
        return []
    found = []
    for line in text.splitlines():
        for rule in RULES:
            if not rule.line.search(line) or not (unit := rule.unit.search(line)):
                continue
            value = last_number(line[max(0, unit.start() - 45): unit.start()])
            if value is None or not rule.broken(value):
                continue
            clause, cited = _CLAUSE_NUMBER.search(line), _CITED.search(line)
            found.append(_violation(rule, value, clause.group(1) if clause else "", cited.group(1) if cited else ""))
    return found


@dataclass(frozen=True)
class QualitativeRule:
    topic: re.Pattern
    required: tuple[re.Pattern, ...]
    article: str
    what: str


QUALITATIVE_RULES = [
    QualitativeRule(re.compile(r"(?iu)не\s*выдерж[^\W\d_]*\s+испытани|неудовлетворительн[^\W\d_]*\s+результат[^\W\d_]*\s+испытани"),
                    (re.compile(r"(?iu)уведом"), re.compile(r"(?iu)причин")), "37",
                    "при неудовлетворительном результате испытания работодатель обязан письменно уведомить работника "
                    "с указанием причин (ст. 37 ТК РК); пункт этого не предусматривает"),
]


def qualitative_check(text: str | None) -> list[Violation]:
    if not _applies(text):
        return []
    found = []
    for line in text.splitlines():
        for rule in QUALITATIVE_RULES:
            if rule.topic.search(line) and not all(r.search(line) for r in rule.required):
                clause, cited = _CLAUSE_NUMBER.search(line), _CITED.search(line)
                found.append(Violation(clause.group(1) if clause else "", cited.group(1) if cited else "",
                                       rule.article, rule.what[0].upper() + rule.what[1:] + "."))
    return found


class _Condition(BaseModel):
    kind: str
    value: int
    clause: str = ""
    citedArticle: str = ""

    @field_validator("clause", "citedArticle", mode="before")
    @classmethod
    def _str(cls, v):
        return "" if v is None else str(v)


class _Conditions(BaseModel):
    conditions: list[_Condition] = []


class LaborNormChecker:
    SYSTEM = ("Ты — модуль извлечения условий трудового договора (Республика Казахстан). Найди в тексте числовые "
              "условия и верни их списком conditions:\n"
              "- vacation_days — продолжительность ежегодного оплачиваемого отпуска в календарных днях;\n"
              "- probation_months — испытательный срок в месяцах;\n"
              "- weekly_hours — продолжительность рабочего времени в часах в неделю.\n"
              "clause — номер пункта договора (например 3.1), citedArticle — номер статьи, на которую ссылается "
              "этот пункт (только число), пусто если ссылки нет. Не выдумывай условия, которых нет в тексте.")

    def __init__(self, llm: Completer | None = None, input_limit: int = 12000):
        self.llm, self.limit = llm, input_limit

    async def check(self, text: str | None) -> list[Violation]:
        return await self._numeric(text) + qualitative_check(text)

    async def _numeric(self, text: str | None) -> list[Violation]:
        if not _applies(text):
            return []
        lines = [l for l in text.splitlines() if any(r.line.search(l) for r in RULES)]
        if not lines:
            return []
        try:
            out = await self.llm.structured(self.SYSTEM, text[:self.limit], _Conditions) if self.llm else None
        except Exception as e:
            log.warning("labor condition extraction failed, regex fallback: %s", e)
            out = None
        if out is None:
            return regex_check(text)
        found, seen = [], set()
        for c in out.conditions:
            rule = _BY_KIND.get(c.kind.strip().lower())
            if rule is None or not rule.broken(c.value) or (key := (c.clause.strip(), rule.article)) in seen:
                continue
            seen.add(key)
            found.append(_violation(rule, c.value, c.clause.strip(), re.sub(r"[^0-9]", "", c.citedArticle)))
        return found
