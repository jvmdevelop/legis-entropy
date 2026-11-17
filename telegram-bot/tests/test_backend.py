import httpx
import pytest

from bot.backend import Backend, BackendError, user_id_for


def make_backend(handler):
    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return Backend("http://docs", "http://brain", client=client, poll_interval_s=0)


async def test_upload_sends_user_header_and_returns_id():
    seen = {}

    def handler(request: httpx.Request):
        seen["user"] = request.headers["X-User-Id"]
        seen["path"] = request.url.path
        return httpx.Response(200, json={"documentId": "doc-1"})

    backend = make_backend(handler)
    assert await backend.upload(user_id_for(42), "dogovor.pdf", b"%PDF") == "doc-1"
    assert seen == {"user": "tg-42", "path": "/api/user-documents/upload"}


async def test_upload_bad_request_shows_service_message():
    backend = make_backend(lambda r: httpx.Response(400, json={"error": "Unsupported file type"}))
    with pytest.raises(BackendError, match="Unsupported file type"):
        await backend.upload("tg-1", "x.exe", b"")


async def test_waits_until_document_is_ready():
    statuses = iter(["UPLOADED", "INDEXING", "READY"])
    calls = []

    def handler(request):
        calls.append(request.url.path)
        return httpx.Response(200, json={"status": next(statuses)})

    await make_backend(handler).wait_until_indexed("tg-1", "doc-1", timeout_s=5)
    assert len(calls) == 3


async def test_indexing_error_is_reported():
    backend = make_backend(lambda r: httpx.Response(200, json={"status": "ERROR", "errorMessage": "пустой PDF"}))
    with pytest.raises(BackendError, match="пустой PDF"):
        await backend.wait_until_indexed("tg-1", "doc-1", timeout_s=5)


async def test_ask_posts_document_and_returns_content():
    def handler(request):
        import json
        body = json.loads(request.content)
        assert body == {"message": "о чем документ", "userId": "tg-7", "documentId": "doc-9"}
        return httpx.Response(200, json={"content": "## Краткий вывод", "tokens": 1})

    assert await make_backend(handler).ask("tg-7", "о чем документ", "doc-9", 10) == "## Краткий вывод"


async def test_ask_without_document_omits_field():
    def handler(request):
        assert b"documentId" not in request.content
        return httpx.Response(200, json={"content": "ok"})

    assert await make_backend(handler).ask("tg-7", "что такое ТК РК", None, 10) == "ok"


async def test_ask_timeout_becomes_user_message():
    def handler(request):
        raise httpx.ReadTimeout("slow", request=request)

    with pytest.raises(BackendError, match="не успел"):
        await make_backend(handler).ask("tg-1", "q", None, 1)


async def test_audit_returns_markdown_only_when_there_are_findings():
    def handler(request):
        assert request.url.path == "/api/brain/documents/doc-1/audit"
        assert request.headers["X-User-Id"] == "tg-1"
        return httpx.Response(200, json={"markdown": "### Проверка", "findings": [{"verdict": "OK"}]})

    assert await make_backend(handler).audit("tg-1", "doc-1", 10) == "### Проверка"

    empty = make_backend(lambda r: httpx.Response(200, json={"markdown": "сырой отчёт", "findings": []}))
    assert await empty.audit("tg-1", "doc-1", 10) is None


async def test_audit_failure_is_not_fatal():
    def handler(request):
        raise httpx.ConnectError("down", request=request)

    assert await make_backend(handler).audit("tg-1", "doc-1", 10) is None
    assert await make_backend(lambda r: httpx.Response(500)).audit("tg-1", "doc-1", 10) is None
