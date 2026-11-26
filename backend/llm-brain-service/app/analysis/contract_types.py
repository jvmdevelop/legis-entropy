import logging
from typing import Callable, Protocol

from app.ml.classifier import Classifier

log = logging.getLogger(__name__)

OTHER = "иной"
LABELS: dict[str, str] = {
    "трудовой": "Трудовой договор: работник и работодатель, должность, заработная плата, рабочее время, отпуск",
    "аренды": "Договор аренды (имущественного найма): арендодатель передаёт арендатору имущество за арендную плату",
    "подряда": "Договор подряда: подрядчик выполняет работы по заданию заказчика, смета, приёмка результата работ",
    "услуг": "Договор возмездного оказания услуг: исполнитель оказывает услуги заказчику за вознаграждение",
    "поставки": "Договор поставки: поставщик (продавец) передаёт товар покупателю, количество, качество, цена",
    OTHER: "Иной договор или документ, не относящийся к трудовому, аренде, подряду, услугам или поставке",
}


class ContractTypeDetector(Protocol):
    def detect(self, text: str) -> str | None: ...


class KeywordDetector:
    def __init__(self, label: str, rule: Callable[[str], bool]):
        self.label, self.rule = label, rule

    def detect(self, text: str) -> str | None:
        return self.label if self.rule(text.lower()) else None


def _any(*words: str) -> Callable[[str], bool]:
    return lambda low: any(w in low for w in words)


RULE_DETECTORS: list[ContractTypeDetector] = [
    KeywordDetector("трудовой", _any("трудов", "работник", "работодатель")),
    KeywordDetector("аренды", _any("арен", "найм", "арендатор")),
    KeywordDetector("поставки", lambda l: "поставк" in l or "покупатель" in l or ("продавец" in l and "товар" in l)),
    KeywordDetector("услуг", _any("услуг", "исполнитель", "заказчик")),
    KeywordDetector("подряда", _any("подряд", "подрядчик")),
]


class ContractTypeService:
    def __init__(self, classifier: Classifier | None, rules: list[ContractTypeDetector] | None = None,
                 min_score: float = 0.4, input_limit: int = 4000):
        self.classifier = classifier
        self.rules = RULE_DETECTORS if rules is None else rules
        self.min_score = min_score
        self.limit = input_limit

    async def detect(self, text: str) -> str:
        for d in self.rules:
            if label := d.detect(text):
                return label
        if self.classifier is None:
            return OTHER
        try:
            p = await self.classifier.classify(text[:self.limit], LABELS)
        except Exception as e:
            log.warning("contract type classifier failed: %s", e)
            return OTHER
        return p.label if p.label in LABELS and p.score >= self.min_score else OTHER
