import asyncio
import logging
from typing import Awaitable, Callable, TypeVar

log = logging.getLogger(__name__)
R = TypeVar("R")


def is_rate_limit(e: BaseException | None) -> bool:
    while e is not None:
        m = str(e).lower()
        if "429" in m or "rate limit" in m or "too many requests" in m:
            return True
        e = e.__cause__
    return False


class RateLimitRetry:
    def __init__(self, wait_s: float = 20.0, sleep: Callable[[float], Awaitable[None]] = asyncio.sleep):
        self.wait_s = wait_s
        self._sleep = sleep

    async def run(self, call: Callable[[], Awaitable[R]]) -> R:
        try:
            return await call()
        except Exception as e:
            if not is_rate_limit(e):
                raise
            log.warning("LLM rate limit hit - waiting %ss before retry", self.wait_s)
            await self._sleep(self.wait_s)
            return await call()
