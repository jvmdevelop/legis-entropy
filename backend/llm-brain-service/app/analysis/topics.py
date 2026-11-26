import re
from dataclasses import dataclass

_L = r"[^\W\d_]"


@dataclass(frozen=True)
class Topic:
    pattern: re.Pattern
    article: str
    title: str


def _t(pattern: str, article: str, title: str) -> Topic:
    return Topic(re.compile(rf"(?iu){pattern}"), article, title)


TK_TOPICS: list[Topic] = [
    _t(rf"раздел{_L}*\s+(?:на\s+части|отпуск)|отпуск{_L}*[^.]{{0,40}}раздел{_L}*\s+на\s+части",
       "92", "разделение оплачиваемого ежегодного трудового отпуска на части"),
    _t(rf"отпуск{_L}*\s+продолжительност|продолжительност{_L}*\s+(?:{_L}+\s+){{0,3}}отпуск", "88",
       "продолжительность основного оплачиваемого ежегодного отпуска"),
    _t(rf"перен[её]с{_L}*\s+отпуск|отпуск{_L}*[^.]{{0,40}}перен[её]с{_L}*", "94", "перенесение отпуска"),
    _t(rf"(?:двух|2)\s+экземпляр", "33", "порядок заключения трудового договора, экземпляры"),
    _t(rf"испытательн{_L}*\s+срок", "36", "испытательный срок"),
    _t(rf"(?:предупредив|уведомив)\s+работодател", "56", "расторжение по инициативе работника"),
    _t(rf"перерыв{_L}*\s+для\s+отдыха", "81", "перерывы для отдыха и приёма пищи"),
    _t(rf"сверхурочн", "108", "оплата сверхурочной работы"),
    _t(rf"продолжительност{_L}*\s+(?:еженедельн{_L}*\s+)?рабоч{_L}*\s+(?:недел|времен)|сорока\s+часов|40\s+час",
       "68", "нормальная продолжительность рабочего времени"),
]


def expected_article(clause: str | None, law_code: str | None) -> Topic | None:
    if not clause or (law_code or "").upper().replace(" ", "") not in {"ТКРК", "414-V"}:
        return None
    hits = {t.article: t for t in TK_TOPICS if t.pattern.search(clause)}
    return next(iter(hits.values())) if len(hits) == 1 else None
