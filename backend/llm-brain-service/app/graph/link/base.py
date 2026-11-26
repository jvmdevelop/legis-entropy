from dataclasses import dataclass
from enum import Enum

from app.schemas import BrainRequest


class SubjectKind(str, Enum):
    DOCUMENT = "DOCUMENT"
    SITUATION = "SITUATION"
    VOICE_EVIDENCE = "VOICE_EVIDENCE"

    @property
    def neo4j_label(self) -> str:
        return {"DOCUMENT": "UserDocument", "SITUATION": "Situation", "VOICE_EVIDENCE": "VoiceEvidence"}[self.value]


@dataclass(frozen=True)
class LinkableSubject:
    kind: SubjectKind
    id: str
    user_id: str = ""
    label: str | None = None

    def __post_init__(self):
        if not self.id or not self.id.strip():
            raise ValueError("id is required")


class SubjectLinker:
    kind: SubjectKind
    priority: int = 10
    display_name: str = ""
    display_accusative: str = ""
    display_genitive: str = ""

    def can_handle(self, req: BrainRequest) -> bool:
        raise NotImplementedError

    async def resolve(self, req: BrainRequest) -> LinkableSubject | None:
        raise NotImplementedError

    async def fetch_text(self, subject: LinkableSubject, graph_id: str | None) -> str:
        raise NotImplementedError

    async def link_to_law(self, graph_id: str, subject: LinkableSubject, law_code: str, country: str) -> bool:
        raise NotImplementedError

    async def link_clause_to_article(self, graph_id: str, subject: LinkableSubject, law_code: str, country: str,
                                     article_number: str, clause_ref: str, subject_snippet: str,
                                     article_snippet: str, confidence: float) -> bool:
        return False

    async def flag_conflict(self, graph_id: str, subject: LinkableSubject, law_code: str, country: str,
                            article_number: str, clause_ref: str, reason: str, confidence: float) -> bool:
        return False

    async def suggested_law_codes(self, subject: LinkableSubject, graph_id: str | None) -> list[str]:
        return []


class SubjectLinkerRegistry:
    def __init__(self, linkers: list[SubjectLinker]):
        self._linkers = sorted(linkers, key=lambda l: l.priority)

    def select_for(self, req: BrainRequest) -> SubjectLinker | None:
        return next((l for l in self._linkers if l.can_handle(req)), None)

    def by_kind(self, kind: SubjectKind) -> SubjectLinker | None:
        return next((l for l in self._linkers if l.kind == kind), None)

    async def resolve(self, req: BrainRequest) -> tuple[SubjectLinker, LinkableSubject] | None:
        linker = self.select_for(req)
        subject = await linker.resolve(req) if linker else None
        return (linker, subject) if subject else None

    def all(self) -> list[SubjectLinker]:
        return list(self._linkers)
