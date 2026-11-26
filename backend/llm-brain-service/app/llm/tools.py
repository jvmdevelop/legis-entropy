import json
import logging
from dataclasses import dataclass
from typing import Any, Awaitable, Callable

from pydantic import BaseModel

log = logging.getLogger(__name__)

ToolFn = Callable[[Any], Awaitable[Any]]


@dataclass
class Tool:
    name: str
    description: str
    args: type[BaseModel]
    fn: ToolFn

    def schema(self) -> dict:
        params = self.args.model_json_schema()
        params.pop("title", None)
        return {"type": "function",
                "function": {"name": self.name, "description": self.description, "parameters": params}}


class ToolRegistry:
    def __init__(self) -> None:
        self._tools: dict[str, Tool] = {}

    def register(self, name: str, description: str, args: type[BaseModel], fn: ToolFn) -> None:
        self._tools[name] = Tool(name, description, args, fn)

    def tool(self, name: str, description: str, args: type[BaseModel]):
        def deco(fn: ToolFn) -> ToolFn:
            self.register(name, description, args, fn)
            return fn
        return deco

    def names(self) -> list[str]:
        return list(self._tools)

    def get(self, name: str) -> Tool | None:
        return self._tools.get(name)

    def schemas(self, names: "list[str] | None" = None) -> list[dict]:
        if names is None:
            return [t.schema() for t in self._tools.values()]
        return [self._tools[n].schema() for n in names if n in self._tools]

    async def call(self, name: str, raw_args: str | dict | None) -> str:
        tool = self._tools.get(name)
        if tool is None:
            return f"Ошибка: неизвестный инструмент {name}"
        try:
            data = json.loads(raw_args) if isinstance(raw_args, str) and raw_args.strip() else (raw_args or {})
            result = await tool.fn(tool.args.model_validate(data))
        except Exception as e:
            log.warning("Tool %s failed: %s", name, e)
            return f"Ошибка инструмента {name}: {e}"
        if isinstance(result, str):
            return result
        if isinstance(result, BaseModel):
            return result.model_dump_json(by_alias=True)
        return json.dumps(result, ensure_ascii=False, default=str)
