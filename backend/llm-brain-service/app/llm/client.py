import logging
from typing import AsyncIterator, Protocol, TypeVar

from pydantic import BaseModel

from app.config import Settings
from app.llm.messages import MessageBuilder
from app.llm.provider import ChatBackend, OpenAiBackend
from app.llm.retry import is_rate_limit  # noqa: F401  
from app.llm.structured import StructuredOutput, extract_json  # noqa: F401  
from app.llm.tool_loop import ToolLoop
from app.llm.tools import ToolRegistry
from app.schemas import BrainResponse, HistoryEntry

log = logging.getLogger(__name__)
T = TypeVar("T", bound=BaseModel)

FAILED_ANSWER = "Не удалось получить ответ от LLM."


class Completer(Protocol):
    async def complete(self, system_prompt: str, user_prompt: str) -> str: ...

    async def structured(self, system_prompt: str, user_prompt: str, schema: type[T]) -> T | None: ...


class ChatModel(Completer, Protocol):
    async def answer(self, user_prompt: str, history: list[HistoryEntry] | None = None,
                     use_tools: bool = True, tools: list[str] | None = None) -> BrainResponse: ...

    def stream(self, user_prompt: str, history: list[HistoryEntry] | None = None,
               use_tools: bool = True, tools: list[str] | None = None) -> AsyncIterator[str]: ...


class LLMClient:
    def __init__(self, backend: ChatBackend, tools: ToolRegistry, messages: MessageBuilder | None = None):
        self.backend = backend
        self.tools = tools
        self.messages = messages or MessageBuilder()
        self._loop = ToolLoop(backend, tools)
        self._structured = StructuredOutput(self.complete)

    @classmethod
    def from_settings(cls, s: Settings, tools: ToolRegistry, messages: MessageBuilder | None = None) -> "LLMClient":
        return cls(OpenAiBackend.from_settings(s), tools, messages)

    @property
    def provider(self):
        return self.backend.provider


    async def answer(self, user_prompt: str, history: list[HistoryEntry] | None = None,
                     use_tools: bool = True, tools: list[str] | None = None) -> BrainResponse:
        use_tools = use_tools and tools != []
        try:
            r = await self._loop.run(self.messages.chat(user_prompt, history), use_tools, tools)
            return BrainResponse(content=r.content, tokens=r.tokens)
        except Exception as e:
            if use_tools and self.tools.names():
                log.warning("LLM tool call failed (%s). Falling back without tools.", e)
                return await self.answer(user_prompt, history, use_tools=False)
            log.error("LLM call failed: %s", e)
            return BrainResponse(content=FAILED_ANSWER, tokens=0)

    def stream(self, user_prompt: str, history: list[HistoryEntry] | None = None,
               use_tools: bool = True, tools: list[str] | None = None) -> AsyncIterator[str]:
        return self._loop.stream(self.messages.chat(user_prompt, history), use_tools and tools != [], tools)

    async def complete(self, system_prompt: str, user_prompt: str) -> str:
        resp = await self.backend.create(MessageBuilder.plain(system_prompt, user_prompt), structured=True)
        return resp.choices[0].message.content or ""

    async def structured(self, system_prompt: str, user_prompt: str, schema: type[T]) -> T | None:
        return await self._structured(system_prompt, user_prompt, schema)
