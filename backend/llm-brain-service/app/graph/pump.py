import asyncio
from typing import Any, AsyncIterator, Awaitable, Callable

from app.sse import Sse


class EventPump:
    def __init__(self) -> None:
        self._q: asyncio.Queue[Sse | None] = asyncio.Queue()
        self.result: Any = None

    def emit(self, event: Sse) -> None:
        self._q.put_nowait(event)

    async def run(self, work: Callable[[], Awaitable[Any]]) -> AsyncIterator[Sse]:
        task = asyncio.ensure_future(work())
        task.add_done_callback(lambda _: self._q.put_nowait(None))
        try:
            while (ev := await self._q.get()) is not None:
                yield ev
            self.result = task.result()
        finally:
            if not task.done():
                task.cancel()
