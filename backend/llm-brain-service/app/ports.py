from typing import Protocol

from app.schemas import AuditFinding, EditProposal


class AuditResult(Protocol):
    findings: list[AuditFinding]
    summary: str
    markdown: str


class ReferenceAuditor(Protocol):
    async def audit(self, text: str) -> AuditResult: ...


class EditSuggester(Protocol):
    async def suggest_for_text(self, text: str) -> tuple[list[EditProposal], str]: ...


class DocumentEditor(EditSuggester, Protocol):
    async def suggest_with_audit(self, text: str): ...

    async def apply(self, document_id: str, user_id: str, proposals: list[EditProposal], title: str | None = None): ...


class ReferenceReports(Protocol):
    async def build_report(self, text: str, compact: bool = False) -> str: ...

    async def exists(self, law_code: str, number: int) -> bool: ...


class LinkedLawRef(Protocol):
    code: str
    title: str
    score: float


class LawNameLinker(Protocol):
    async def link(self, name: str, country: str = "RK") -> LinkedLawRef | None: ...


class LawGraphWriter(Protocol):
    async def add_law_codes(self, graph_id: str, codes: list[str], country: str) -> list[str]: ...
