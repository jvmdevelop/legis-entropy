import json
from dataclasses import dataclass
from typing import Any, AsyncIterator

from pydantic import BaseModel
from fastapi.responses import StreamingResponse


@dataclass
class Sse:
    data: str
    event: str | None = None

    @staticmethod
    def of(data: Any, event: str | None = None) -> "Sse":
        if isinstance(data, BaseModel):
            data = data.model_dump_json(by_alias=True)
        elif not isinstance(data, str):
            data = json.dumps(data, ensure_ascii=False, default=str)
        return Sse(data, event)

    @staticmethod
    def thinking(payload: dict) -> "Sse":
        return Sse.of(payload, "thinking")

    def as_dict(self) -> dict:
        return {"data": self.data, **({"event": self.event} if self.event else {})}


def encode(e: Sse) -> str:
    out = f"event:{e.event}\n" if e.event else ""
    for line in e.data.split("\n"):
        out += f"data:{line}\n"
    return out + "\n"


def sse_response(events: AsyncIterator[Sse]) -> StreamingResponse:
    async def gen():
        async for e in events:
            yield encode(e)
    return StreamingResponse(gen(), media_type="text/event-stream",
                             headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"})
