import logging
from typing import AsyncIterator

from app.graph.actions import GraphActionService
from app.pipeline.types import ContextPlan, TaskType
from app.schemas import BrainRequest, BrainResponse
from app.sse import Sse

log = logging.getLogger(__name__)


class GraphLinkDocumentHandler:
    def __init__(self, actions: GraphActionService):
        self.actions = actions

    def supports(self, task: TaskType) -> bool:
        return task == TaskType.GRAPH_LINK_DOCUMENT

    async def handle(self, req: BrainRequest, plan: ContextPlan) -> BrainResponse:
        log.info("Pipeline shortcut: GRAPH_LINK_SUBJECT for graph=%s (doc=%s, sit=%s, voice=%s)",
                 req.graph_id, req.document_id, req.situation_id, req.voice_id)
        result = await self.actions.link_request_subject_to_laws(req, 5, "RK")
        return BrainResponse(content=result.report, tokens=0)

    async def stream(self, req: BrainRequest, plan: ContextPlan) -> AsyncIterator[Sse]:
        yield Sse.of((await self.handle(req, plan)).content)
