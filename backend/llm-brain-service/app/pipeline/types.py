from dataclasses import dataclass
from enum import Enum


class TaskType(str, Enum):
    DOCUMENT_AUDIT = "DOCUMENT_AUDIT"
    DOCUMENT_QA = "DOCUMENT_QA"
    LAW_SEARCH = "LAW_SEARCH"
    SUMMARY = "SUMMARY"
    GENERAL_LEGAL_ADVICE = "GENERAL_LEGAL_ADVICE"
    GRAPH_LINK_DOCUMENT = "GRAPH_LINK_DOCUMENT"
    GRAPH_ADD_LAWS = "GRAPH_ADD_LAWS"
    GRAPH_DEEP_ANALYSIS = "GRAPH_DEEP_ANALYSIS"
    DOCUMENT_DRAFT = "DOCUMENT_DRAFT"

    @property
    def requires_graph(self) -> bool:
        return self in (TaskType.GRAPH_LINK_DOCUMENT, TaskType.GRAPH_ADD_LAWS, TaskType.GRAPH_DEEP_ANALYSIS)

    @property
    def is_document_task(self) -> bool:
        return self in (TaskType.DOCUMENT_AUDIT, TaskType.DOCUMENT_QA, TaskType.SUMMARY)


class RetrievalMode(str, Enum):
    USER_DOCUMENT = "USER_DOCUMENT"
    LAW = "LAW"
    HYBRID_LEGAL = "HYBRID_LEGAL"
    NONE = "NONE"


class Profession(str, Enum):
    FAMILY_LAWYER = "FAMILY_LAWYER"
    REAL_ESTATE_LAWYER = "REAL_ESTATE_LAWYER"
    CRIMINAL_LAWYER = "CRIMINAL_LAWYER"
    CORPORATE_LAWYER = "CORPORATE_LAWYER"
    LABOR_LAWYER = "LABOR_LAWYER"
    GENERAL_LAWYER = "GENERAL_LAWYER"


@dataclass(frozen=True)
class ContextPlan:
    task: TaskType
    retrieval: RetrievalMode
    confidence: float = 1.0
    professions: tuple[Profession, ...] = (Profession.GENERAL_LAWYER,)
