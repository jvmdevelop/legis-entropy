import json
from types import SimpleNamespace as NS

from app.llm import text_tool_calls as ttc
from app.llm.tool_loop import FINAL_NUDGE, ToolLoop

XML = "<tool_call>\n<function=getLawGraph>\n<parameter=code>\nТК\n</parameter>\n<parameter=country>\nRK\n</parameter>\n</function>\n</tool_call>"


def test_parse_xml_and_json_forms():
    calls, rest = ttc.parse("Смотрю закон.\n" + XML, {"getLawGraph"})
    assert [(c.name, json.loads(c.arguments)) for c in calls] == [("getLawGraph", {"code": "ТК", "country": "RK"})]
    assert rest == "Смотрю закон."
    calls, _ = ttc.parse('<tool_call>{"name": "searchLaws", "arguments": {"query": "отпуск"}}</tool_call>')
    assert calls[0].name == "searchLaws" and json.loads(calls[0].arguments) == {"query": "отпуск"}
    assert ttc.parse(XML, {"other"})[0] == []


def test_stream_guard_hides_call_split_across_chunks():
    g = ttc.StreamGuard()
    out = "".join(g.feed(c) for c in ["Ответ: 3 мес", "яца. <to", "ol_call>\n<function=x>", "...</tool_call>"])
    assert out + g.flush() == "Ответ: 3 месяца. " and g.capturing and g.captured.startswith("<tool_call>")
    g = ttc.StreamGuard()
    assert "".join(g.feed(c) for c in ["a <", "b"]) + g.flush() == "a <b"


class Tools:
    def __init__(self):
        self.calls = []

    def schemas(self, names=None):
        return [{"type": "function", "function": {"name": "getLawGraph", "parameters": {}}}]

    async def call(self, name, args):
        self.calls.append((name, json.loads(args)))
        return "ТК РК: Трудовой кодекс"


def msg(content=None, tool_calls=None):
    return NS(choices=[NS(message=NS(content=content, tool_calls=tool_calls))], usage=None)


class Backend:
    provider = NS(name="t")

    def __init__(self, replies):
        self.replies, self.seen = list(replies), []

    async def create(self, msgs, stream=False, **kw):
        self.seen.append((list(msgs), kw))
        r = self.replies.pop(0)
        if not stream:
            return msg(r)

        async def gen():
            for part in [r[i:i + 7] for i in range(0, len(r), 7)]:
                yield NS(choices=[NS(delta=NS(content=part, tool_calls=None))], usage=None)
        return gen()


async def test_run_executes_text_tool_call_then_answers():
    tools = Tools()
    loop = ToolLoop(Backend([XML, "Испытательный срок — до трёх месяцев."]), tools)
    res = await loop.run([{"role": "user", "content": "q"}])
    assert res.content == "Испытательный срок — до трёх месяцев."
    assert tools.calls == [("getLawGraph", {"code": "ТК", "country": "RK"})]


async def test_run_final_round_call_is_stripped_and_nudged():
    b = Backend([XML, XML, "Итог: до трёх месяцев."])
    loop = ToolLoop(b, Tools(), max_rounds=1)
    res = await loop.run([{"role": "user", "content": "q"}])
    assert res.content == "Итог: до трёх месяцев."
    assert b.seen[-1][0][-1] == {"role": "user", "content": FINAL_NUDGE}


async def test_stream_never_shows_raw_tool_call():
    tools = Tools()
    loop = ToolLoop(Backend(["Ищу норму. " + XML, "Срок — до трёх месяцев."]), tools)
    out = "".join([c async for c in loop.stream([{"role": "user", "content": "q"}])])
    assert "<tool_call" not in out and "function=" not in out
    assert out == "Ищу норму. Срок — до трёх месяцев."
    assert tools.calls == [("getLawGraph", {"code": "ТК", "country": "RK"})]
