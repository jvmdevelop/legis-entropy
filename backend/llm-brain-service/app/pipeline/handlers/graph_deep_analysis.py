import logging
from typing import AsyncIterator

from app.graph.deep import DeepAnalysisService, classify_depth
from app.graph.link.base import SubjectLinkerRegistry
from app.graph.pump import EventPump
from app.pipeline.types import ContextPlan, TaskType
from app.schemas import BrainRequest, BrainResponse
from app.sse import Sse

log = logging.getLogger(__name__)

NO_SUBJECT = ("Глубокий анализ требует активный объект в графе — выберите документ, ситуацию или ГС "
              "и повторите запрос.")


class GraphDeepAnalysisHandler:
    def __init__(self, deep: DeepAnalysisService, registry: SubjectLinkerRegistry):
        self.deep, self.registry = deep, registry

    def supports(self, task: TaskType) -> bool:
        return task == TaskType.GRAPH_DEEP_ANALYSIS

    async def handle(self, req: BrainRequest, plan: ContextPlan) -> BrainResponse:
        resolved = await self.registry.resolve(req)
        if resolved is None:
            return BrainResponse(content=NO_SUBJECT, tokens=0)
        linker, subject = resolved
        depth = classify_depth(req.message)
        log.info("Pipeline shortcut: GRAPH_DEEP_ANALYSIS for graph=%s kind=%s id=%s depth=%d",
                 req.graph_id, linker.kind.value, subject.id, depth)
        result = await self.deep.run(req.graph_id, linker, subject, "RK", depth, None)
        return BrainResponse(content=result.report, tokens=0)

    async def stream(self, req: BrainRequest, plan: ContextPlan) -> AsyncIterator[Sse]:
        depth = classify_depth(req.message)
        yield Sse.thinking({"step": "classify", "task": "GRAPH_DEEP_ANALYSIS", "depth": depth})
        resolved = await self.registry.resolve(req)
        if resolved is None:
            yield Sse.of(NO_SUBJECT)
            return
        linker, subject = resolved
        pump = EventPump()
        try:
            async for ev in pump.run(lambda: self.deep.run(req.graph_id, linker, subject, "RK", depth,
                                                           lambda p: pump.emit(Sse.of(p, "deep-analysis-progress")))):
                yield ev
        except Exception as e:
            log.error("Deep analysis failed: %s", e, exc_info=True)
            yield Sse.of(f"Глубокий анализ прерван: {e}", "error")
            return
        yield Sse.of(pump.result.report)
