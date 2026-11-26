import logging
from typing import AsyncIterator

from app.draft.service import DocumentDraftService
from app.pipeline.types import ContextPlan, TaskType
from app.schemas import BrainRequest, BrainResponse
from app.sse import Sse

log = logging.getLogger(__name__)


class DocumentDraftHandler:
    def __init__(self, drafts: DocumentDraftService):
        self.drafts = drafts

    def supports(self, task: TaskType) -> bool:
        return task == TaskType.DOCUMENT_DRAFT

    async def handle(self, req: BrainRequest, plan: ContextPlan) -> BrainResponse:
        log.info("Pipeline shortcut: DOCUMENT_DRAFT for user=%s graphId=%s", req.user_id, req.graph_id)
        return await self.drafts.draft(req)

    def stream(self, req: BrainRequest, plan: ContextPlan) -> AsyncIterator[Sse]:
        log.info("Pipeline shortcut: DOCUMENT_DRAFT stream for user=%s graphId=%s", req.user_id, req.graph_id)
        return self.drafts.stream_draft(req)
