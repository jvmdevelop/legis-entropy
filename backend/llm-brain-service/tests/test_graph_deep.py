import json

from app.graph.deep import classify_depth
from app.graph.link.base import LinkableSubject, SubjectKind
from app.pipeline.types import ContextPlan, RetrievalMode, TaskType
from app.schemas import BrainRequest
from tests.test_graph_support import CONTRA, FakeLLM, FakeNli, make_container

PLAN = ContextPlan(TaskType.GRAPH_DEEP_ANALYSIS, RetrievalMode.NONE)


def test_depth_keywords():
    assert classify_depth("Сделай идеальный анализ") == 3
    assert classify_depth("максимально глубокий разбор") == 3
    assert classify_depth("проанализируй документ") == 2


async def _run(c, depth):
    deep = c.f.deep
    linker = c.f.linkers.by_kind(SubjectKind.DOCUMENT)
    events = []
    res = await deep.run("g", linker, LinkableSubject(SubjectKind.DOCUMENT, "d"), "RK", depth, events.append)
    return res, events


async def test_deep_analysis_respects_limits_and_emits_progress():
    llm = FakeLLM(complete=json.dumps([f"L{i}" for i in range(12)]))
    c = make_container(llm=llm, nli=FakeNli(CONTRA), deep_max_laws_total=9, deep_max_conflicts=4,
                       deep_max_articles_total=7)
    res, events = await _run(c, 3)
    s = c.settings
    assert res.primary_laws == s.deep_primary_laws == 5
    assert res.primary_laws + res.secondary_laws + res.tertiary_laws <= 9
    assert res.secondary_laws <= 5 * s.deep_related_hop2
    assert res.articles_linked == 7
    assert res.conflicts_found == 4
    assert len(c.graph.clause_links) == 7
    assert len(c.situations.doc_conflicts) + len(c.situations.article_conflicts) == 4
    phases = [e.phase for e in events]
    assert phases[0] == "start" and phases[-1] == "done"
    for p in ("references", "primary_laws", "articles", "related_laws_2", "related_laws_3", "conflicts", "persist"):
        assert p in phases
    assert "## Глубокий анализ документа" in res.report and "идеальный" in res.report
    assert c.graph.comments[0]["kind"] == "DEEP_ANALYSIS" and c.graph.comments[0]["subjectKind"] == "UserDocument"


async def test_depth2_skips_conflicts_and_third_hop():
    c = make_container(nli=FakeNli(CONTRA))
    res, events = await _run(c, 2)
    assert res.tertiary_laws == 0 and res.conflicts_found == 0
    assert "conflicts" not in {e.phase for e in events}
    assert c.situations.doc_conflicts == [] and c.situations.article_conflicts == []


async def test_neutral_nli_flags_nothing():
    c = make_container(llm=FakeLLM(complete='["ТК РК"]'))
    res, _ = await _run(c, 3)
    assert res.conflicts_found == 0 and c.situations.doc_conflicts == []


async def test_handler_stream_event_sequence():
    c = make_container()
    h = c.handlers.resolve(TaskType.GRAPH_DEEP_ANALYSIS)
    req = BrainRequest(message="идеальный анализ", graph_id="g", document_id="d")
    events = [e async for e in h.stream(req, PLAN)]
    assert events[0].event == "thinking"
    assert json.loads(events[0].data) == {"step": "classify", "task": "GRAPH_DEEP_ANALYSIS", "depth": 3}
    progress = [json.loads(e.data) for e in events if e.event == "deep-analysis-progress"]
    assert progress and set(progress[0]) == {"phase", "done", "total", "message"}
    assert events[-1].event is None and events[-1].data.startswith("## Глубокий анализ")


async def test_handler_without_subject():
    c = make_container()
    h = c.handlers.resolve(TaskType.GRAPH_DEEP_ANALYSIS)
    events = [e async for e in h.stream(BrainRequest(message="анализ", graph_id="g"), PLAN)]
    assert [e.event for e in events] == ["thinking", None] and "требует активный объект" in events[1].data
