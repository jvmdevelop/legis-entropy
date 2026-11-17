from collections.abc import AsyncIterable, AsyncIterator
from dataclasses import dataclass


@dataclass(frozen=True)
class SseEvent:
    name: str
    data: str


async def parse_sse(lines: AsyncIterable[str]) -> AsyncIterator[SseEvent]:
    name = "message"
    data: list[str] = []
    async for raw in lines:
        line = raw.rstrip("\r")
        if line == "":
            if data:
                yield SseEvent(name, "\n".join(data))
            name, data = "message", []
        elif line.startswith("event:"):
            name = line[len("event:"):].strip() or "message"
        elif line.startswith("data:"):
            data.append(line[len("data:"):])
    if data:
        yield SseEvent(name, "\n".join(data))
