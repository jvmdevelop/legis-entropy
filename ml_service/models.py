from enum import Enum

from pydantic import BaseModel


class DocumentStatus(str, Enum):
    active = "active"
    outdated = "outdated"
    unknown = "unknown"


class DocumentInput(BaseModel):
    id: str
    title: str
    text: str
    status: DocumentStatus


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


class CompareRequest(BaseModel):
    doc_a: DocumentInput
    doc_b: DocumentInput


class CompareResponse(BaseModel):
    similarity: float
    assessment: str


class DocMeta(BaseModel):
    title: str
    status: str
    ref_count: int = 0
    article_count: int = 0
    is_amendment: bool = False


class ReviewRequest(BaseModel):
    doc_a: DocMeta
    doc_b: DocMeta
    similarity: float
    assessment: str
    shared_issues: list[str] = []


class ReviewResponse(BaseModel):
    review: str
    model_ready: bool


class CorpusReviewRequest(BaseModel):
    total_docs: int
    active_count: int
    outdated_count: int
    with_issues: int
    issue_types: dict[str, int] = {}
    most_problematic: list[str] = []


class CorpusReviewResponse(BaseModel):
    review: str
    model_ready: bool
