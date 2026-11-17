import asyncio

import httpx

from bot.backend import Backend
from bot.live import CURSOR, LiveAnswer, StatusTicker, describe_thinking
from bot.sse import SseEvent, parse_sse

RAW = (
    'event:thinking\ndata:{"step":"classify","task":"LAW_SEARCH"}\n\n'
    'event:thinking\ndata:{"step":"retrieve","found":6}\n\n'
    "data:##\n\n"
    "data: П\n\n"
    "data:родолжительность\n\n"
    "data:К\ndata:\ndata:Основной\n\n"
    "event:redaction\ndata:итог\ndata:второй абзац\n\n"
)


async def lines(text):
    for line in text.split("\n"):
        yield line


async def collect(aiter):
    return [x async for x in aiter]


async def test_parser_keeps_leading_spaces_and_joins_multiline_data():
    events = await collect(parse_sse(lines(RAW)))
    assert [e.name for e in events] == ["thinking", "thinking", "message", "message", "message", "message", "redaction"]
    text = "".join(e.data for e in events if e.name == "message")
    assert text == "## Продолжительность" + "К\n\nОсновной"
    assert events[-1].data == "итог\nвторой абзац"


async def test_backend_stream_yields_events():
    def handler(request):
        assert request.url.path == "/api/brain/chat/stream"
        return httpx.Response(200, content=RAW.encode(), headers={"content-type": "text/event-stream"})

    backend = Backend("http://docs", "http://brain", client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))
    events = await collect(backend.stream("tg-1", "q", None, 10))
    assert events[0] == SseEvent("thinking", '{"step":"classify","task":"LAW_SEARCH"}')


def test_thinking_stages_are_human_readable():
    assert describe_thinking('{"step":"retrieve","found":6}') == "Нашёл 6 фрагментов законов и документа"
    assert describe_thinking("not json") is None


class FakeMessage:
    def __init__(self, log):
        self.log = log
        self.text = None

    async def edit_text(self, text, parse_mode=None):
        self.text = text
        self.log.append(("edit", text))

    async def answer(self, text, parse_mode=None):
        m = FakeMessage(self.log)
        m.text = text
        self.log.append(("send", text))
        return m


async def fake_stream(chunks, delay=0.0):
    for c in chunks:
        await asyncio.sleep(delay)
        yield c


async def test_live_answer_streams_then_applies_redaction(monkeypatch):
    monkeypatch.setattr("bot.live.EDIT_INTERVAL_S", 0.01)
    log = []
    first = FakeMessage(log)
    status = StatusTicker(first, "Думаю").start()
    live = LiveAnswer(first, status=status)
    live.show.set()
    events = fake_stream([
        SseEvent("thinking", '{"step":"retrieve","found":3}'),
        SseEvent("message", "## Ответ\n\n**24** дня"),
        SseEvent("message", " по ст. 88"),
        SseEvent("redaction", "## Ответ\n\n**24** календарных дня по ст. 88 ТК РК"),
        SseEvent("done", ""),
    ], delay=0.03)
    await asyncio.gather(live.consume(events), live.render())

    assert first.text == "<b>Ответ</b>\n\n<b>24</b> календарных дня по ст. 88 ТК РК"
    assert any(t.endswith(CURSOR) for kind, t in log if kind == "edit")
    assert any("·" in t and "с" in t for kind, t in log if kind == "edit")


async def test_long_answer_spills_into_new_messages(monkeypatch):
    monkeypatch.setattr("bot.live.EDIT_INTERVAL_S", 0.01)
    log = []
    first = FakeMessage(log)
    live = LiveAnswer(first)
    live.show.set()
    body = "\n".join(f"строка {i} " + "x" * 80 for i in range(120))
    await asyncio.gather(live.consume(fake_stream([SseEvent("message", body), SseEvent("done", "")])), live.render())
    assert len(live.messages) >= 3
    assert all(len(m.text) <= 4096 for m in live.messages)


async def test_error_before_any_text_is_shown():
    log = []
    first = FakeMessage(log)
    live = LiveAnswer(first)
    live.show.set()
    await asyncio.gather(live.consume(fake_stream([SseEvent("error", "сломалось")])), live.render())
    assert first.text == "⚠️ сломалось"


async def test_answer_cut_without_done_is_marked():
    log = []
    first = FakeMessage(log)
    live = LiveAnswer(first)
    live.show.set()
    await asyncio.gather(live.consume(fake_stream([SseEvent("message", "Многие ссылки ведут на статьи, которые регу")])),
                         live.render())
    assert first.text.startswith("Многие ссылки ведут на статьи, которые регу")
    assert "Ответ оборвался" in first.text


async def test_stream_sends_history():
    import json

    def handler(request):
        body = json.loads(request.content)
        assert body["history"] == [{"role": "USER", "content": "q1"}, {"role": "ASSISTANT", "content": "a1"}]
        return httpx.Response(200, content=b"data:ok\n\nevent:done\ndata:\n\n")

    backend = Backend("http://docs", "http://brain", client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))
    events = await collect(backend.stream("tg-1", "ты уверена?", "doc", 10,
                                          [{"role": "USER", "content": "q1"}, {"role": "ASSISTANT", "content": "a1"}]))
    assert [e.name for e in events] == ["message", "done"]
