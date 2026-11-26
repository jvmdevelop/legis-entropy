import asyncio
import logging
from collections.abc import AsyncIterator

import httpx

from .sse import SseEvent, parse_sse

log = logging.getLogger(__name__)

ANALYSIS_PROMPT = "о чем документ"


class BackendError(Exception):
    pass


def user_id_for(telegram_user_id: int) -> str:
    return f"tg-{telegram_user_id}"


class Backend:
    def __init__(self, document_service_url: str, brain_url: str, client: httpx.AsyncClient | None = None,
                 poll_interval_s: float = 2.0):
        self._docs = document_service_url
        self._brain = brain_url
        self._client = client or httpx.AsyncClient(timeout=httpx.Timeout(30.0))
        self._poll = poll_interval_s

    async def close(self) -> None:
        await self._client.aclose()

    async def upload(self, user_id: str, filename: str, content: bytes) -> str:
        resp = await self._client.post(
            f"{self._docs}/api/user-documents/upload",
            headers={"X-User-Id": user_id},
            files={"file": (filename, content)},
        )
        if resp.status_code == 400:
            raise BackendError(resp.json().get("error", "Файл не принят сервисом документов."))
        if resp.status_code != 200:
            log.warning("upload failed: %s %s", resp.status_code, resp.text[:300])
            raise BackendError("Не удалось загрузить документ. Попробуйте ещё раз.")
        return resp.json()["documentId"]

    async def wait_until_indexed(self, user_id: str, document_id: str, timeout_s: float) -> None:
        loop = asyncio.get_running_loop()
        deadline = loop.time() + timeout_s
        while True:
            resp = await self._client.get(
                f"{self._docs}/api/user-documents/{document_id}/status",
                headers={"X-User-Id": user_id},
            )
            if resp.status_code == 200:
                body = resp.json()
                status = body.get("status")
                if status == "READY":
                    return
                if status in ("ERROR", "DELETED"):
                    raise BackendError(body.get("errorMessage") or "Не удалось прочитать документ.")
            if loop.time() >= deadline:
                raise BackendError("Документ слишком долго обрабатывается. Попробуйте позже.")
            await asyncio.sleep(self._poll)

    async def ask(self, user_id: str, message: str, document_id: str | None, timeout_s: float) -> str:
        payload = {"message": message, "userId": user_id}
        if document_id:
            payload["documentId"] = document_id
        try:
            resp = await self._client.post(
                f"{self._brain}/api/brain/chat",
                headers={"X-User-Id": user_id},
                json=payload,
                timeout=httpx.Timeout(timeout_s, connect=10.0),
            )
        except httpx.TimeoutException as e:
            raise BackendError("ИИ не успел ответить. Попробуйте ещё раз чуть позже.") from e
        if resp.status_code != 200:
            log.warning("brain failed: %s %s", resp.status_code, resp.text[:300])
            raise BackendError("Сервис анализа сейчас недоступен.")
        body = resp.json()
        return body.get("content") or body.get("answer") or ""

    async def audit(self, user_id: str, document_id: str, timeout_s: float) -> str | None:
        try:
            resp = await self._client.post(
                f"{self._brain}/api/brain/documents/{document_id}/audit",
                headers={"X-User-Id": user_id},
                timeout=httpx.Timeout(timeout_s, connect=10.0),
            )
        except httpx.HTTPError as e:
            log.warning("audit request failed: %s", e)
            return None
        if resp.status_code != 200:
            log.warning("audit failed: %s %s", resp.status_code, resp.text[:300])
            return None
        body = resp.json()
        return body.get("markdown") if body.get("findings") else None

    async def review(self, user_id: str, document_id: str, timeout_s: float) -> dict | None:
        try:
            resp = await self._client.post(
                f"{self._brain}/api/brain/documents/{document_id}/edits/suggest",
                headers={"X-User-Id": user_id},
                timeout=httpx.Timeout(timeout_s, connect=10.0),
            )
        except httpx.HTTPError as e:
            log.warning("review request failed: %s", e)
            return None
        if resp.status_code != 200:
            log.warning("review failed: %s %s", resp.status_code, resp.text[:300])
            return None
        return resp.json()

    async def apply_edits(self, user_id: str, document_id: str, proposals: list[dict],
                          timeout_s: float) -> dict | None:
        try:
            resp = await self._client.post(
                f"{self._brain}/api/brain/documents/{document_id}/edits/apply",
                headers={"X-User-Id": user_id},
                json={"proposals": proposals},
                timeout=httpx.Timeout(timeout_s, connect=10.0),
            )
        except httpx.HTTPError as e:
            log.warning("apply edits request failed: %s", e)
            return None
        if resp.status_code != 200:
            log.warning("apply edits failed: %s %s", resp.status_code, resp.text[:300])
            return None
        body = resp.json()
        return body if body.get("documentId") else None

    async def download(self, user_id: str, document_id: str) -> bytes | None:
        try:
            resp = await self._client.get(
                f"{self._docs}/api/user-documents/{document_id}/file",
                headers={"X-User-Id": user_id},
            )
        except httpx.HTTPError as e:
            log.warning("download failed: %s", e)
            return None
        return resp.content if resp.status_code == 200 and resp.content else None

    async def stream(self, user_id: str, message: str, document_id: str | None,
                     timeout_s: float, history: list[dict] | None = None) -> AsyncIterator[SseEvent]:
        payload = {"message": message, "userId": user_id}
        if document_id:
            payload["documentId"] = document_id
        if history:
            payload["history"] = history
        try:
            async with self._client.stream(
                "POST", f"{self._brain}/api/brain/chat/stream",
                headers={"X-User-Id": user_id, "Accept": "text/event-stream"},
                json=payload,
                timeout=httpx.Timeout(timeout_s, connect=10.0),
            ) as resp:
                if resp.status_code != 200:
                    log.warning("stream failed: %s", resp.status_code)
                    raise BackendError("Сервис анализа сейчас недоступен.")
                async for event in parse_sse(resp.aiter_lines()):
                    yield event
        except httpx.TimeoutException as e:
            raise BackendError("ИИ не успел ответить. Попробуйте ещё раз чуть позже.") from e
