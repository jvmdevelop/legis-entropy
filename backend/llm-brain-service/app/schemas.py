from typing import Any

from pydantic import BaseModel, ConfigDict, field_validator
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, coerce_numbers_to_str=True)


class HistoryEntry(CamelModel):
    role: str
    content: str


class BrainRequest(CamelModel):
    message: str
    user_id: str | None = None
    document_id: str | None = None
    graph_id: str | None = None
    situation_id: str | None = None
    voice_id: str | None = None
    history: list[HistoryEntry] = []

    @field_validator("history", mode="before")
    @classmethod
    def _none_history(cls, v: Any) -> Any:
        return v or []

    @staticmethod
    def _has(v: str | None) -> bool:
        return bool(v and v.strip())

    @property
    def has_document(self) -> bool:
        return self._has(self.document_id)

    @property
    def has_graph(self) -> bool:
        return self._has(self.graph_id)

    @property
    def has_situation(self) -> bool:
        return self._has(self.situation_id)

    @property
    def has_voice(self) -> bool:
        return self._has(self.voice_id)


class BrainResponse(CamelModel):
    content: str
    tokens: int = 0


class CompareRequest(CamelModel):
    left_kind: str | None = None
    left_id: str | None = None
    left_label: str | None = None
    right_kind: str | None = None
    right_id: str | None = None
    right_label: str | None = None
    user_id: str | None = None


class RiskScanRequest(CamelModel):
    text: str | None = None
    country: str = "RK"

    @field_validator("country", mode="before")
    @classmethod
    def _default_country(cls, v: Any) -> Any:
        return v if v and str(v).strip() else "RK"


class ClauseRiskResult(CamelModel):
    clause_id: str
    clause_text: str
    risk_level: str
    law_citation: str | None = None
    reason: str | None = None


class MissingClauseResult(CamelModel):
    required_clause: str
    law_basis: str


class RiskScanResponse(CamelModel):
    contract_type: str
    clauses: list[ClauseRiskResult] = []
    missing_clauses: list[MissingClauseResult] = []
    total_violations: int = 0
    total_warnings: int = 0
    total_compliant: int = 0


class AuditFinding(CamelModel):
    clause: str | None = None
    law_code: str | None = None
    cited_article: str | None = None
    verdict: str
    correct_article: str | None = None
    explanation: str | None = None
    confidence: float | None = None
    source: str | None = None
    page: int | None = None
    verification: str | None = None
    verification_note: str | None = None

    @property
    def is_problem(self) -> bool:
        return self.verdict != "OK"


class AuditResponse(CamelModel):
    summary: str
    markdown: str
    problems: int
    findings: list[AuditFinding] = []


class ProgressEvent(CamelModel):
    phase: str
    done: int
    total: int
    message: str


class RetrievalChunk(CamelModel):
    text: str = ""
    document_id: str | None = None
    file_name: str | None = None
    page: int | None = None
    chunk_index: int | None = None
    score: float | None = None
    metadata: dict[str, Any] = {}


class EditProposal(CamelModel):
    id: str
    clause: str
    original: str
    replacement: str
    reason: str = ""
    verdict: str = ""
    law_basis: str | None = None
    source: str = "rule"
    page: int | None = None


class EditSuggestResponse(CamelModel):
    document_id: str
    summary: str = ""
    proposals: list[EditProposal] = []
    markdown: str = ""
    problems: int = 0


class EditRewriteRequest(CamelModel):
    original: str
    instruction: str | None = None


class EditApplyRequest(CamelModel):
    proposals: list[EditProposal]
    title: str | None = None


class EditSkip(CamelModel):
    id: str
    reason: str


class EditApplyResponse(CamelModel):
    document_id: str | None
    file_name: str | None
    applied: list[str] = []
    skipped: list[EditSkip] = []


class PlaybookRule(CamelModel):
    id: str = ""
    kind: str = "text"
    text: str = ""
    clause_type: str | None = None
    party: str | None = None
    modality: str | None = None
    max_days: int | None = None


class Playbook(CamelModel):
    id: str = ""
    name: str
    rules: list[PlaybookRule] = []


class PlaybookResult(CamelModel):
    rule_id: str
    rule: str
    status: str
    clause: str | None = None
    page: int | None = None
    quote: str | None = None
    explanation: str = ""


class PlaybookCheckResponse(CamelModel):
    playbook_id: str
    name: str
    results: list[PlaybookResult] = []
    summary: str = ""
