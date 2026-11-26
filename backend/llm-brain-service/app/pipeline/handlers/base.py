import logging
from typing import AsyncIterator, Protocol

from app.pipeline.types import ContextPlan, TaskType
from app.schemas import BrainRequest, BrainResponse
from app.sse import Sse

log = logging.getLogger(__name__)


class TaskHandler(Protocol):
    def supports(self, task: TaskType) -> bool: ...

    async def handle(self, req: BrainRequest, plan: ContextPlan) -> BrainResponse: ...

    def stream(self, req: BrainRequest, plan: ContextPlan) -> AsyncIterator[Sse]: ...


class HandlerRegistry:
    def __init__(self) -> None:
        self._handlers: list[TaskHandler] = []
        self._default: TaskHandler | None = None

    def register(self, handler: TaskHandler) -> None:
        self._handlers.append(handler)

    def set_default(self, handler: TaskHandler) -> None:
        self._default = handler

    def resolve(self, task: TaskType) -> TaskHandler:
        for h in self._handlers:
            if h.supports(task):
                return h
        if self._default is None:
            raise LookupError(f"no handler for {task}")
        return self._default
