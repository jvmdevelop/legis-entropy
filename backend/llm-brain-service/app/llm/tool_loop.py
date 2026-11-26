from dataclasses import dataclass
from typing import AsyncIterator

from app.llm import text_tool_calls as ttc
from app.llm.provider import ChatBackend, record_usage
from app.llm.tools import ToolRegistry

MAX_TOOL_ROUNDS = 6
FINAL_NUDGE = ("Инструменты больше недоступны. Не вызывай инструменты и не пиши <tool_call>. "
               "Дай итоговый ответ пользователю по уже найденной информации.")


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

    def _known(self, kw: dict) -> set[str]:
        return {s["function"]["name"] for s in kw.get("tools", [])}

    async def _execute(self, msgs: list[dict], calls: list[tuple[str, str, str]]) -> None:
        msgs.append({"role": "assistant", "content": None, "tool_calls": [
            {"id": cid, "type": "function", "function": {"name": name, "arguments": args}}
            for cid, name, args in calls]})
        for cid, name, args in calls:
            msgs.append({"role": "tool", "tool_call_id": cid, "content": await self.tools.call(name, args)})

    @staticmethod
    def _text_calls(content: str | None, known: set[str], round_no: int) -> list[tuple[str, str, str]]:
        calls, _ = ttc.parse(content or "", known)
        return [(f"text-{round_no}-{i}", c.name, c.arguments) for i, c in enumerate(calls)]

    async def _final(self, msgs: list[dict]) -> tuple[str, int]:
        resp = await self.backend.create(msgs)
        tokens = resp.usage.total_tokens if resp.usage else 0
        content = resp.choices[0].message.content or ""
        if not ttc.has_call(content):
            return content, tokens
        rest = ttc.parse(content)[1]
        if rest:
            return rest, tokens
        resp = await self.backend.create(msgs + [{"role": "user", "content": FINAL_NUDGE}])
        tokens += resp.usage.total_tokens if resp.usage else 0
        return ttc.parse(resp.choices[0].message.content or "")[1], tokens

    async def run(self, msgs: list[dict], use_tools: bool = True, names: list[str] | None = None) -> LoopResult:
        tokens, kw = 0, self._schemas(use_tools, names)
        known = self._known(kw)
        for round_no in range(self.max_rounds):
            resp = await self.backend.create(msgs, **kw)
            tokens += resp.usage.total_tokens if resp.usage else 0
            msg = resp.choices[0].message
            if msg.tool_calls:
                msgs.append(msg.model_dump(exclude_none=True))
                for call in msg.tool_calls:
                    msgs.append({"role": "tool", "tool_call_id": call.id,
                                 "content": await self.tools.call(call.function.name, call.function.arguments)})
                continue
            text_calls = self._text_calls(msg.content, known, round_no)
            if text_calls:
                await self._execute(msgs, text_calls)
                continue
            if not ttc.has_call(msg.content):
                return LoopResult(msg.content or "", tokens)
            rest = ttc.parse(msg.content)[1]
            if rest:
                return LoopResult(rest, tokens)
            break
        content, more = await self._final(msgs)
        return LoopResult(content, tokens + more)

    async def stream(self, msgs: list[dict], use_tools: bool = True,
                     names: list[str] | None = None) -> AsyncIterator[str]:
        kw = self._schemas(use_tools, names)
        known = self._known(kw)
        for round_no in range(self.max_rounds):
            calls: dict[int, dict] = {}
            guard = ttc.StreamGuard()
            async for chunk in await self.backend.create(msgs, stream=True, **kw):
                if getattr(chunk, "usage", None):
                    record_usage(self.backend.provider.name, chunk.usage)
                if not chunk.choices:
                    continue
                delta = chunk.choices[0].delta
                if delta.content and (out := guard.feed(delta.content)):
                    yield out
                for tc in delta.tool_calls or []:
                    c = calls.setdefault(tc.index, {"id": "", "name": "", "args": ""})
                    c["id"] = tc.id or c["id"]
                    if tc.function:
                        c["name"] += tc.function.name or ""
                        c["args"] += tc.function.arguments or ""
            if tail := guard.flush():
                yield tail
            if calls:
                await self._execute(msgs, [(c["id"], c["name"], c["args"]) for c in (calls[i] for i in sorted(calls))])
                continue
            if not guard.capturing:
                return
            text_calls = self._text_calls(guard.captured, known, round_no)
            if not text_calls:
                break
            await self._execute(msgs, text_calls)
        guard = ttc.StreamGuard()
        async for chunk in await self.backend.create(msgs, stream=True):
            if chunk.choices and chunk.choices[0].delta.content and (out := guard.feed(chunk.choices[0].delta.content)):
                yield out
        if tail := guard.flush():
            yield tail
        if guard.capturing:
            resp = await self.backend.create(msgs + [{"role": "user", "content": FINAL_NUDGE}])
            if text := ttc.parse(resp.choices[0].message.content or "")[1]:
                yield text
