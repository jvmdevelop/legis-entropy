import logging
from dataclasses import dataclass

from app.ml import text as T
from app.ml.embeddings import Embedder
from app.schemas import MissingClauseResult

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class RequiredClause:
    name: str
    law_basis: str
    keywords: tuple[str, ...]

    @property
    def description(self) -> str:
        return f"{self.name}: {', '.join(self.keywords)}"


def _rc(name: str, basis: str, *kw: str) -> RequiredClause:
    return RequiredClause(name, basis, kw)


REGISTRY: dict[str, list[RequiredClause]] = {
    "трудовой": [
        _rc("Должность / трудовая функция", "ст. 28 ТК РК", "должност", "функц", "обязанност"),
        _rc("Размер и порядок выплаты заработной платы", "ст. 28 ТК РК", "заработн", "оклад", "зарплат", "вознаграждени"),
        _rc("Место выполнения работы", "ст. 28 ТК РК", "место работ", "местом работ", "место выполнения работ",
            "место исполнения трудов", "по адресу"),
        _rc("Реквизиты сторон (наименование, БИН/ИИН, местонахождение работодателя; ФИО и документ работника)",
            "ст. 28 ТК РК", "реквизиты сторон", "бин ", "бин:", "иин ", "иин:", "удостоверение личности",
            "местонахождени", "юридический адрес"),
        _rc("Дата начала работы", "ст. 28 ТК РК", "дата начал", "приступить", "с «", "с \""),
        _rc("Режим рабочего времени", "ст. 68 ТК РК", "режим", "рабочее время", "рабочий день", "график"),
    ],
    "аренды": [
        _rc("Предмет аренды (описание имущества)", "ст. 541 ГК РК", "предмет", "имущество", "объект", "помещени"),
        _rc("Размер арендной платы", "ст. 546 ГК РК", "арендн", "плата", "оплата", "сумма"),
        _rc("Срок аренды", "ст. 545 ГК РК", "срок", "период", "продолжительност"),
        _rc("Порядок передачи имущества", "ст. 547 ГК РК", "акт приема", "акт приёма", "акт-приема", "приема-передачи",
            "приёма-передачи", "передается по акту", "передаётся по акту", "порядок передачи"),
    ],
    "услуг": [
        _rc("Предмет договора (перечень услуг)", "ст. 683 ГК РК", "предмет", "перечень услуг", "виды услуг", "оказывает"),
        _rc("Стоимость услуг и порядок оплаты", "ст. 685 ГК РК", "стоимост", "цена", "оплат", "вознаграждени"),
        _rc("Сроки оказания услуг", "ст. 683 ГК РК", "срок", "период", "в течени"),
        _rc("Порядок приёмки услуг", "ст. 684 ГК РК", "приемк", "сдач", "акт выполн", "акт оказан"),
    ],
    "поставки": [
        _rc("Наименование и количество товара", "ст. 458 ГК РК", "наименовани", "количество", "объем", "товар"),
        _rc("Цена и порядок оплаты", "ст. 458 ГК РК", "цена", "стоимост", "оплат"),
        _rc("Срок поставки", "ст. 458 ГК РК", "срок поставк", "срок отгрузк", "дата доставк"),
        _rc("Условия о качестве товара", "ст. 422 ГК РК", "качество", "стандарт", "гост", "технические условия"),
    ],
}
REGISTRY["подряда"] = [
    _rc("Предмет договора (содержание работ)", "ст. 616 ГК РК", "предмет", "выполнить работ", "работы по"),
    _rc("Цена работы", "ст. 621 ГК РК", "цена", "стоимост", "смет"),
    _rc("Сроки выполнения работы", "ст. 620 ГК РК", "срок выполнен", "срок начал", "срок окончан", "в течение"),
    _rc("Приёмка выполненных работ", "ст. 630 ГК РК", "приемк", "акт выполненн", "сдач"),
]
DEFAULT: list[RequiredClause] = [
    _rc("Предмет договора", "ст. 393 ГК РК", "предмет", "цель договора"),
    _rc("Права и обязанности сторон", "ст. 393 ГК РК", "права", "обязанност", "стороны обязуются"),
    _rc("Порядок расторжения договора", "ст. 401 ГК РК", "расторжени", "прекращени", "отказ от договора"),
]


def required_for(contract_type: str) -> list[RequiredClause]:
    return REGISTRY.get(contract_type, DEFAULT)


class RequiredClauseChecker:
    def __init__(self, embedder: Embedder | None, threshold: float = 0.6, max_segments: int = 50, segment_len: int = 400):
        self.embedder = embedder
        self.threshold = threshold
        self.max_segments = max_segments
        self.segment_len = segment_len

    def _segments(self, text: str, clauses: list[str]) -> list[str]:
        if clauses:
            return clauses[:self.max_segments]
        return [text[i:i + self.segment_len] for i in range(0, len(text), self.segment_len)][:self.max_segments]

    async def _semantic_hits(self, required: list[RequiredClause], segments: list[str]) -> list[bool]:
        if self.embedder is None or not segments:
            return [False] * len(required)
        try:
            vecs = await self.embedder.embed([r.description for r in required] + segments)
        except Exception as e:
            log.warning("required-clause embedding failed, keyword match only: %s", e)
            return [False] * len(required)
        req_v, seg_v = vecs[:len(required)], vecs[len(required):]
        return [max(T.cosine(r, s) for s in seg_v) >= self.threshold for r in req_v]

    async def missing(self, text: str, contract_type: str, clauses: list[str] | None = None) -> list[MissingClauseResult]:
        lower = text.lower()
        required = required_for(contract_type)
        semantic = await self._semantic_hits(required, self._segments(text, clauses or []))
        return [MissingClauseResult(required_clause=r.name, law_basis=r.law_basis)
                for r, sem in zip(required, semantic) if not sem and not any(k in lower for k in r.keywords)]
