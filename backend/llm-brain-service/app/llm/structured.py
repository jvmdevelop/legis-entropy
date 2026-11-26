import json
import logging
import re
from typing import Awaitable, Callable, TypeVar

from pydantic import BaseModel, ValidationError

log = logging.getLogger(__name__)
T = TypeVar("T", bound=BaseModel)
Complete = Callable[[str, str], Awaitable[str]]


def extract_json(text: str) -> str:
    text = (text or "").strip()
    fence = re.search(r"```(?:json)?\s*(.*?)```", text, re.S)
    if fence:
        text = fence.group(1).strip()
    starts = [i for i in (text.find("{"), text.find("[")) if i >= 0]
    if not starts:
        return text
    start = min(starts)
    end = max(text.rfind("}"), text.rfind("]"))
    return text[start:end + 1] if end > start else text[start:]


def _unwrap_array(raw: str):
    try:
        data = json.loads(raw)
    except ValueError:
        return None
    if isinstance(data, dict):
        lists = [v for v in data.values() if isinstance(v, list)]
        if len(lists) == 1:
            return lists[0]
    return None


def _is_array_schema(schema: type[BaseModel]) -> bool:
    return schema.model_json_schema().get("type") == "array"


class StructuredOutput:
    def __init__(self, complete: Complete, attempts: int = 2):
        self._complete = complete
        self.attempts = attempts

    async def __call__(self, system_prompt: str, user_prompt: str, schema: type[T]) -> T | None:
        shape = ("JSON-МАССИВОМ (начинается с '['), не объектом" if _is_array_schema(schema)
                 else "JSON-объектом")
        sys = (f"{system_prompt}\n\nОтветь ТОЛЬКО {shape}, данные — по JSON Schema (саму схему не повторяй):\n"
               f"{json.dumps(schema.model_json_schema(), ensure_ascii=False)}")
        text, prompt = "", user_prompt
        for _ in range(self.attempts):
            try:
                text = await self._complete(sys, prompt)
                raw = extract_json(text)
                try:
                    return schema.model_validate_json(raw)
                except ValidationError:
                    inner = _unwrap_array(raw) if _is_array_schema(schema) else None
                    if inner is None:
                        raise
                    return schema.model_validate(inner)
            except ValidationError as e:
                log.debug("structured output invalid (%s): %s", schema.__name__, e)
                prompt = f"{user_prompt}\n\nПредыдущий ответ был невалидным JSON:\n{text[:2000]}\nИсправь."
            except Exception as e:
                log.warning("structured LLM call failed: %s", e)
                return None
        return None
