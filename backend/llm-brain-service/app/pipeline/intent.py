import logging
from dataclasses import dataclass

from app.ml.classifier import Classifier
from app.pipeline import keywords as K
from app.pipeline.text import contains_any, contains_any_word, normalize
from app.pipeline.types import ContextPlan, RetrievalMode, TaskType
from app.schemas import BrainRequest

log = logging.getLogger(__name__)

TASK_LABELS: dict[str, str] = {
    TaskType.DOCUMENT_AUDIT.value: "Проверить, проанализировать документ или договор пользователя: риски, ошибки, "
                                   "нарушения закона, спорные условия.",
    TaskType.DOCUMENT_QA.value: "Ответить на вопрос по содержанию прикреплённого документа: что написано, сроки, "
                                "суммы, обязанности сторон, пункт договора.",
    TaskType.SUMMARY.value: "Кратко пересказать, резюмировать, выделить суть или главное.",
    TaskType.LAW_SEARCH.value: "Найти закон, статью, норму права, кодекс, правовое основание или ответственность "
                               "по законодательству.",
    TaskType.GENERAL_LEGAL_ADVICE.value: "Общая юридическая консультация: что делать, как поступить, какие права, "
                                         "куда обращаться.",
    TaskType.DOCUMENT_DRAFT.value: "Составить, написать или подготовить юридический документ: иск, заявление, "
                                   "жалобу, претензию, договор, доверенность.",
    TaskType.GRAPH_ADD_LAWS.value: "Добавить законы или нормы в граф пользователя.",
    TaskType.GRAPH_LINK_DOCUMENT.value: "Связать документ, ситуацию или голосовое сообщение с законами в графе.",
    TaskType.GRAPH_DEEP_ANALYSIS.value: "Глубокий, комплексный, всесторонний анализ всех связанных норм в графе.",
}

DEFAULT_RETRIEVAL: dict[TaskType, RetrievalMode] = {
    TaskType.DOCUMENT_AUDIT: RetrievalMode.USER_DOCUMENT,
    TaskType.DOCUMENT_QA: RetrievalMode.USER_DOCUMENT,
    TaskType.SUMMARY: RetrievalMode.USER_DOCUMENT,
    TaskType.LAW_SEARCH: RetrievalMode.LAW,
    TaskType.GENERAL_LEGAL_ADVICE: RetrievalMode.NONE,
    TaskType.DOCUMENT_DRAFT: RetrievalMode.LAW,
    TaskType.GRAPH_ADD_LAWS: RetrievalMode.NONE,
    TaskType.GRAPH_LINK_DOCUMENT: RetrievalMode.NONE,
    TaskType.GRAPH_DEEP_ANALYSIS: RetrievalMode.NONE,
}


@dataclass(frozen=True)
class RuleResult:
    plan: ContextPlan
    decided: bool


def _looks_like_question(m: str) -> bool:
    return "?" in m or m.startswith(K.QUESTION_PREFIXES) or contains_any(m, K.QUESTION_VERBS)


def _looks_like_legal_advice(m: str) -> bool:
    return contains_any(m, K.GENERAL_ADVICE) or contains_any(m, K.LEGAL_ADVICE_EXTRA)


def _looks_like_draft(m: str) -> bool:
    if not contains_any_word(m, K.DRAFT_TYPE):
        return False
    if contains_any_word(m, K.DRAFT_ACTION):
        return True
    return contains_any_word(m, K.DRAFT_ACTION_INFINITIVE) and contains_any_word(m, K.DRAFT_REQUEST_MARKERS) \
        and "?" not in m


def classify_depth(message: str | None) -> int:
    return 3 if contains_any(normalize(message), K.IDEAL_DEPTH) else 2


def classify_rules(req: BrainRequest) -> RuleResult:
    m = normalize(req.message)
    has_doc = req.has_document
    P = lambda t, r, decided=True: RuleResult(ContextPlan(t, r), decided)  # noqa: E731

    summary = contains_any(m, K.SUMMARY)
    audit = contains_any(m, K.DOCUMENT_AUDIT)
    qa = contains_any(m, K.DOCUMENT_QA) or _looks_like_question(m)
    law = contains_any(m, K.LAW)
    law_search = contains_any(m, K.LAW_SEARCH)
    comparison = contains_any(m, K.COMPARISON)

    if _looks_like_draft(m):
        return P(TaskType.DOCUMENT_DRAFT, RetrievalMode.LAW)
    if contains_any(m, K.DEEP_ANALYSIS) or (contains_any(m, K.DEEP_VERBS) and contains_any(m, K.DEEP_QUALIFIERS)):
        return P(TaskType.GRAPH_DEEP_ANALYSIS, RetrievalMode.NONE)
    has_subject = has_doc or req.has_situation or req.has_voice
    if has_subject and (contains_any(m, K.LINK_SUBJECT) or
                        (contains_any(m, K.LINK_VERBS) and contains_any(m, K.LINK_OBJECTS))):
        return P(TaskType.GRAPH_LINK_DOCUMENT, RetrievalMode.NONE)
    if contains_any(m, K.ADD_LAWS) or (contains_any(m, K.ADD_VERBS) and contains_any(m, K.ADD_TARGETS)):
        return P(TaskType.GRAPH_ADD_LAWS, RetrievalMode.NONE)
    if summary:
        return P(TaskType.SUMMARY, RetrievalMode.USER_DOCUMENT if has_doc else
                 (RetrievalMode.LAW if law or law_search else RetrievalMode.NONE))
    if has_doc and (comparison or (audit and law) or (qa and law_search)):
        return P(TaskType.DOCUMENT_AUDIT, RetrievalMode.HYBRID_LEGAL)
    if has_doc and audit:
        return P(TaskType.DOCUMENT_AUDIT, RetrievalMode.USER_DOCUMENT)
    if has_doc and law and _looks_like_legal_advice(m):
        return P(TaskType.DOCUMENT_QA, RetrievalMode.HYBRID_LEGAL)
    if has_doc:
        return P(TaskType.DOCUMENT_QA, RetrievalMode.USER_DOCUMENT, decided=qa)
    if law or law_search:
        return P(TaskType.LAW_SEARCH, RetrievalMode.LAW)
    if contains_any(m, K.GENERAL_ADVICE):
        return P(TaskType.GENERAL_LEGAL_ADVICE, RetrievalMode.NONE)
    return P(TaskType.GENERAL_LEGAL_ADVICE, RetrievalMode.NONE, decided=False)


class IntentClassifier:
    def __init__(self, classifier: Classifier | None, threshold: float = 0.7):
        self.classifier = classifier
        self.threshold = threshold

    @staticmethod
    def candidates(req: BrainRequest) -> list[TaskType]:
        if req.has_document:
            return [TaskType.DOCUMENT_QA, TaskType.DOCUMENT_AUDIT, TaskType.SUMMARY]
        out = [TaskType.GENERAL_LEGAL_ADVICE, TaskType.LAW_SEARCH, TaskType.DOCUMENT_DRAFT]
        if req.has_graph:
            out += [TaskType.GRAPH_ADD_LAWS, TaskType.GRAPH_DEEP_ANALYSIS]
            if req.has_situation or req.has_voice:
                out.append(TaskType.GRAPH_LINK_DOCUMENT)
        return out

    async def classify(self, req: BrainRequest) -> ContextPlan:
        rule = classify_rules(req)
        if rule.decided or self.classifier is None or not (req.message or "").strip():
            return rule.plan
        labels = {t.value: TASK_LABELS[t.value] for t in self.candidates(req)}
        try:
            pred = await self.classifier.classify(req.message, labels)
        except Exception as e:
            log.warning("ML intent classification failed: %s", e)
            return rule.plan
        if pred.score < self.threshold or pred.label not in labels:
            return rule.plan
        task = TaskType(pred.label)
        log.info("ML intent: %s (%.2f) instead of rule fallback %s", task, pred.score, rule.plan.task)
        return ContextPlan(task, DEFAULT_RETRIEVAL[task], confidence=pred.score)
