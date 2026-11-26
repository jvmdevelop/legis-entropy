from dataclasses import dataclass
from typing import AsyncIterator

from app.llm.provider import ChatBackend, record_usage
from app.llm.tools import ToolRegistry

MAX_TOOL_ROUNDS = 6


@dataclass
class LoopResult:
    content: str
    tokens: int


class ToolLoop:
    def __init__(self, backend: ChatBackend, tools: ToolRegistry, max_rounds: int = MAX_TOOL_ROUNDS):
        self.backend = backend
        self.tools = tools
        self.max_rounds = max_rounds

    def _schemas(self, use_tools: bool, names: list[str] | None = None) -> dict:
        if not use_tools:
            return {}
        schemas = self.tools.schemas(names)
        return {"tools": schemas} if schemas else {}

    async def run(self, msgs: list[dict], use_tools: bool = True, names: list[str] | None = None) -> LoopResult:
        tokens, kw = 0, self._schemas(use_tools, names)
        for _ in range(self.max_rounds):
            resp = await self.backend.create(msgs, **kw)
            tokens += resp.usage.total_tokens if resp.usage else 0
            msg = resp.choices[0].message
            if not msg.tool_calls:
                return LoopResult(msg.content or "", tokens)
            msgs.append(msg.model_dump(exclude_none=True))
            for call in msg.tool_calls:
                msgs.append({"role": "tool", "tool_call_id": call.id,
                             "content": await self.tools.call(call.function.name, call.function.arguments)})
        resp = await self.backend.create(msgs)
        return LoopResult(resp.choices[0].message.content or "", tokens)

    async def stream(self, msgs: list[dict], use_tools: bool = True,
                     names: list[str] | None = None) -> AsyncIterator[str]:
        kw = self._schemas(use_tools, names)
        for _ in range(self.max_rounds):
            calls: dict[int, dict] = {}
            async for chunk in await self.backend.create(msgs, stream=True, **kw):
                if getattr(chunk, "usage", None):
                    record_usage(self.backend.provider.name, chunk.usage)
                if not chunk.choices:
                    continue
                delta = chunk.choices[0].delta
                if delta.content:
                    yield delta.content
                for tc in delta.tool_calls or []:
                    c = calls.setdefault(tc.index, {"id": "", "name": "", "args": ""})
                    c["id"] = tc.id or c["id"]
                    if tc.function:
                        c["name"] += tc.function.name or ""
                        c["args"] += tc.function.arguments or ""
            if not calls:
                return
            ordered = [calls[i] for i in sorted(calls)]
            msgs.append({"role": "assistant", "content": None, "tool_calls": [
                {"id": c["id"], "type": "function", "function": {"name": c["name"], "arguments": c["args"]}}
                for c in ordered]})
            for c in ordered:
                msgs.append({"role": "tool", "tool_call_id": c["id"],
                             "content": await self.tools.call(c["name"], c["args"])})
        async for chunk in await self.backend.create(msgs, stream=True):
            if chunk.choices and chunk.choices[0].delta.content:
                yield chunk.choices[0].delta.content
