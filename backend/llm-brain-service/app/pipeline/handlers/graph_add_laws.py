import logging
import re
from typing import AsyncIterator

from app.graph.actions import GraphActionService
from app.pipeline.types import ContextPlan, TaskType
from app.schemas import BrainRequest, BrainResponse
from app.sse import Sse

log = logging.getLogger(__name__)

ADD_LAWS_STOPWORDS = {
    "добавь", "добавьте", "добавить", "подбери", "подбор", "найди", "найти", "помести", "поместить", "положи",
    "положить", "включи", "включить", "собери", "в", "на", "с", "и", "по", "про", "граф", "графе", "граф.",
    "пожалуйста", "давай", "давайте", "а",
}


def extract_add_laws_query(message: str | None) -> str:
    if not message:
        return ""
    kept = [t for t in message.split() if (k := re.sub(r"[.,!?;]+$", "", t.lower())) and k not in ADD_LAWS_STOPWORDS]
    return " ".join(kept).strip() or message


class GraphAddLawsHandler:
    def __init__(self, actions: GraphActionService):
        self.actions = actions

    def supports(self, task: TaskType) -> bool:
        return task == TaskType.GRAPH_ADD_LAWS

    async def handle(self, req: BrainRequest, plan: ContextPlan) -> BrainResponse:
        query = extract_add_laws_query(req.message)
        log.info("Pipeline shortcut: GRAPH_ADD_LAWS for graph=%s query='%s'", req.graph_id, query)
        result = await self.actions.find_and_add_laws(req.graph_id, query, "RK", 5)
        return BrainResponse(content=result.report, tokens=0)

    async def stream(self, req: BrainRequest, plan: ContextPlan) -> AsyncIterator[Sse]:
        yield Sse.of((await self.handle(req, plan)).content)
