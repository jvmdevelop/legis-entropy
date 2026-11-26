import json

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.api import brain as brain_api
from app.pipeline.types import ContextPlan, Profession, RetrievalMode as R, TaskType as T
from app.schemas import BrainRequest
from tests.test_pipeline_fakes import FakeDocs, FakeGraphActions, FakeLLM, FakeLaws, build, law_hit


async def collect(gen):
    return [(e.event, e.data) for e in [x async for x in gen]]


async def test_stream_event_sequence_with_graph_and_disclaimer():
    laws = FakeLaws(default_hits=[law_hit("Работник вправе расторгнуть трудовой договор", code="414-V")])
    actions = FakeGraphActions()
    llm = FakeLLM(chunks=["Работник может ", "уволиться по ТК РК, предупредив работодателя заранее."])
    pipeline, _ = build(llm=llm, laws=laws, actions=actions)
    ev = await collect(pipeline.stream(BrainRequest(message="какая статья ТК про увольнение работника",
                                                    graph_id="g1")))
    names = [e for e, _ in ev]
    assert names == ["thinking", "thinking", "thinking", None, None, "redaction", "done"]
    assert json.loads(ev[0][1]) == {"step": "classify", "task": "LAW_SEARCH", "retrieval": "LAW",
                                    "profession": "LABOR_LAWYER"}
    assert ev[0][1].startswith('{"step":"classify"')
    assert json.loads(ev[1][1]) == {"step": "retrieve", "found": 1}
    assert json.loads(ev[2][1]) == {"step": "graph", "added": 1}
    assert actions.calls == [("g1", ["414-V"], "RK")]
    assert "adilet.zan.kz" in ev[5][1] and ev[6][1] == ""
    assert "Контекст документа:" in llm.prompts[0]


async def test_stream_no_redaction_when_unchanged():
    pipeline, _ = build(llm=FakeLLM(chunks=["Обратитесь ", "к юристу для консультации."]))
    ev = await collect(pipeline.stream(BrainRequest(message="привет")))
    assert [e for e, _ in ev] == ["thinking", "thinking", None, None, "done"]
    assert json.loads(ev[1][1]) == {"step": "retrieve", "found": 0}


async def test_stream_error_event():
    pipeline, _ = build(llm=FakeLLM(chunks=["часть"], fail_stream=True))
    ev = await collect(pipeline.stream(BrainRequest(message="привет")))
    assert ev[-1] == ("error", "Ошибка при получении ответа от ИИ: boom")


async def test_stream_document_unavailable_stops():
    pipeline, _ = build(docs=FakeDocs(fail_search=True))
    ev = await collect(pipeline.stream(BrainRequest(message="что в документе?", document_id="d")))
    assert [e for e, _ in ev] == ["thinking", "thinking", None]
    assert ev[2][1].startswith("Не удалось обработать прикреплённый документ")


async def test_handle_appends_added_laws():
    laws = FakeLaws(default_hits=[law_hit("норма", code="414-V")])
    pipeline, h = build(llm=FakeLLM("Ответ достаточно длинный, чтобы не было примечания о неполноте вывода."),
                        laws=laws, actions=FakeGraphActions())
    plan = ContextPlan(T.LAW_SEARCH, R.LAW, professions=(Profession.GENERAL_LAWYER,))
    r = await h.handle(BrainRequest(message="закон", graph_id="g"), plan)
    assert r.content.endswith("\n\n---\nДобавлено в граф: 414-V") and r.tokens == 7


def test_api_endpoints():
    pipeline, _ = build(llm=FakeLLM(chunks=["Привет", "!"], answer="Готово, это полный ответ."))

    app = FastAPI()
    app.include_router(brain_api.build_router(pipeline))
    with TestClient(app) as tc:
        assert tc.post("/api/brain/chat", json={"message": "привет", "history": None}).json() == \
            {"content": "Готово, это полный ответ.", "tokens": 7}
        r = tc.post("/api/brain/chat/stream", json={"message": "привет"})
        assert r.status_code == 200 and "event:thinking" in r.text and "data:Привет" in r.text
        assert "event:done" in r.text


def test_sse_wire_format_matches_spring():
    from app.sse import Sse, encode
    assert encode(Sse("Привет")) == "data:Привет\n\n"
    assert encode(Sse("a\nb", "redaction")) == "event:redaction\ndata:a\ndata:b\n\n"
    assert encode(Sse(" лидирующий пробел")) == "data: лидирующий пробел\n\n"
