import asyncio
import contextlib
import json
import logging
import time
from collections.abc import AsyncIterator

from aiogram.exceptions import TelegramBadRequest, TelegramRetryAfter
from aiogram.types import Message

from .backend import BackendError
from .formatting import split_message, to_telegram_html
from .sse import SseEvent

log = logging.getLogger(__name__)

CURSOR = " ▌"
EDIT_INTERVAL_S = 1.5


async def safe_edit(message: Message, text: str) -> None:
    for parse_mode in ("HTML", None):
        try:
            await message.edit_text(text, parse_mode=parse_mode)
            return
        except TelegramRetryAfter as e:
            await asyncio.sleep(e.retry_after)
            return
        except TelegramBadRequest as e:
            if "not modified" in str(e):
                return
            if parse_mode is None:
                log.debug("edit failed: %s", e)


def describe_thinking(data: str) -> str | None:
    try:
        step = json.loads(data)
    except ValueError:
        return None
    match step.get("step"):
        case "classify":
            return "Определяю, о чём вопрос"
        case "retrieve":
            found = step.get("found")
            return f"Нашёл {found} фрагментов законов и документа" if found else "Ищу нормы в базе законов"
        case "graph":
            return "Добавляю законы в граф"
    return None


class StatusTicker:

    def __init__(self, message: Message, stage: str):
        self.message = message
        self.stage = stage
        self._started = time.monotonic()
        self._task: asyncio.Task | None = None

    def text(self) -> str:
        elapsed = int(time.monotonic() - self._started)
        return f"⏳ {self.stage} · {elapsed} с"

    async def _loop(self) -> None:
        while True:
            await safe_edit(self.message, self.text())
            await asyncio.sleep(EDIT_INTERVAL_S)

    def start(self) -> "StatusTicker":
        self._task = asyncio.create_task(self._loop())
        return self

    async def stop(self) -> None:
        if self._task:
            self._task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._task
            self._task = None


class LiveAnswer:

    def __init__(self, first_message: Message, status: StatusTicker | None = None):
        self.messages: list[Message] = [first_message]
        self.rendered: list[str] = []
        self.status = status
        self.buffer = ""
        self.error: str | None = None
        self.completed = False
        self.done = asyncio.Event()
        self.show = asyncio.Event()

    async def consume(self, events: AsyncIterator[SseEvent]) -> None:
        try:
            async for ev in events:
                if ev.name == "message":
                    self.buffer += ev.data
                elif ev.name == "redaction":
                    self.buffer = ev.data
                elif ev.name == "error":
                    self.error = ev.data
                elif ev.name == "done":
                    self.completed = True
                elif ev.name == "thinking" and self.status and (stage := describe_thinking(ev.data)):
                    self.status.stage = stage
        except BackendError as e:
            self.error = str(e)
        except Exception as e:
            log.warning("stream interrupted: %s", e)
            if not self.buffer:
                self.error = "Связь с сервисом анализа прервалась. Попробуйте ещё раз."
        finally:
            if self.buffer and not self.completed:
                reason = self.error or "модель не ответила до конца"
                self.buffer += f"\n\n> ⚠️ Ответ оборвался ({reason}). Спросите ещё раз — обычно со второй попытки проходит."
            self.done.set()

    async def render(self) -> None:
        await self.show.wait()
        while True:
            finished = self.done.is_set()
            if self.buffer and self.status:
                await self.status.stop()
                self.status = None
            if self.buffer:
                await self._paint(self.buffer, cursor=not finished)
            if finished:
                break
            await asyncio.sleep(EDIT_INTERVAL_S)
        if self.status:
            await self.status.stop()
        if not self.buffer:
            await safe_edit(self.messages[0], f"⚠️ {self.error or 'Пустой ответ от сервиса анализа.'}")

    async def _paint(self, markdown: str, cursor: bool) -> None:
        parts = split_message(to_telegram_html(markdown))
        if cursor and parts:
            parts[-1] += CURSOR
        for i, part in enumerate(parts):
            if i < len(self.messages):
                if i >= len(self.rendered) or self.rendered[i] != part:
                    await safe_edit(self.messages[i], part)
            else:
                try:
                    self.messages.append(await self.messages[-1].answer(part))
                except TelegramBadRequest:
                    self.messages.append(await self.messages[-1].answer(part, parse_mode=None))
        self.rendered = parts
