from pydantic import BaseModel
from enum import Enum


class DocumentStatus(str, Enum):
    active = "active"
    outdated = "outdated"
    unknown = "unknown"


class DocumentInput(BaseModel):
    id: str
    title: str
    text: str
    status: DocumentStatus


# ── /analyze ─────────────────────────────────────────────────────────────────

class AnalyzeRequest(BaseModel):
    documents: list[DocumentInput]


class IssueKind(str, Enum):
    duplication = "duplication"
    contradiction = "contradiction"


class Severity(str, Enum):
    low = "low"
    medium = "medium"
    high = "high"


class Issue(BaseModel):
    kind: IssueKind
    severity: Severity
    document_ids: list[str]
    explanation: str
    score: float


class AnalyzeResponse(BaseModel):
    issues: list[Issue]
    model_name: str
    device: str


# ── /search ───────────────────────────────────────────────────────────────────

class SearchRequest(BaseModel):
    query: str
    documents: list[DocumentInput]
    top_k: int = 5


class SearchResult(BaseModel):
    id: str
    title: str
    score: float


class SearchResponse(BaseModel):
    results: list[SearchResult]


# ── /compare ─────────────────────────────────────────────────────────────────

class CompareRequest(BaseModel):
    doc_a: DocumentInput
    doc_b: DocumentInput


class CompareResponse(BaseModel):
    similarity: float
    assessment: str
