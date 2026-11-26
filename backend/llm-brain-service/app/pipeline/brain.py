import asyncio
import dataclasses
import inspect
import logging
from typing import AsyncIterator

from app.pipeline.handlers.base import HandlerRegistry
from app.pipeline.intent import IntentClassifier
from app.pipeline.profession import ProfessionClassifier
from app.pipeline.types import ContextPlan, RetrievalMode, TaskType
from app.schemas import BrainRequest, BrainResponse
from app.sse import Sse

log = logging.getLogger(__name__)


class BrainPipeline:
    def __init__(self, intent: IntentClassifier, profession: ProfessionClassifier, handlers: HandlerRegistry):
        self.intent = intent
        self.profession = profession
        self.handlers = handlers

    async def resolve_plan(self, req: BrainRequest) -> ContextPlan:
        plan, professions = await asyncio.gather(self.intent.classify(req), self.profession.classify(req.message))
        if plan.task.requires_graph and not req.has_graph:
            log.warning("Task %s requires graphId but none provided - falling back to GENERAL_LEGAL_ADVICE",
                        plan.task.value)
            plan = ContextPlan(TaskType.GENERAL_LEGAL_ADVICE, RetrievalMode.NONE)
        return dataclasses.replace(plan, professions=professions)

    async def answer(self, req: BrainRequest) -> BrainResponse:
        plan = await self.resolve_plan(req)
        return await self.handlers.resolve(plan.task).handle(req, plan)

    async def stream(self, req: BrainRequest) -> AsyncIterator[Sse]:
        try:
            plan = await self.resolve_plan(req)
            handler = self.handlers.resolve(plan.task)
        except Exception as e:
            log.error("Could not resolve plan: %s", e)
            yield Sse(f"Ошибка при получении ответа от ИИ: {e}", "error")
            return
        events = handler.stream(req, plan)
        if inspect.isawaitable(events):
            events = await events
        async for event in events:
            yield event
