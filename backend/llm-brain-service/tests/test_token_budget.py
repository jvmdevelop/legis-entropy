from app.llm.messages import MAX_HISTORY_CHARS, MessageBuilder, trim_history
from app.llm.tools import ToolRegistry
from app.pipeline.context import CONTEXT_BUDGET_CHARS, DocumentContextFormatter
from app.pipeline.tool_policy import tools_for
from app.pipeline.types import ContextPlan, RetrievalMode as R, TaskType as T
from app.schemas import BrainRequest, HistoryEntry, RetrievalChunk


def plan(task):
    return ContextPlan(task, R.NONE)


def test_tool_policy_by_task_and_context():
    assert tools_for(BrainRequest(message="x"), plan(T.SUMMARY)) == []
    advice = tools_for(BrainRequest(message="x"), plan(T.GENERAL_LEGAL_ADVICE))
    assert advice == ["searchLaws", "findArticle"]
    law_no_graph = tools_for(BrainRequest(message="x"), plan(T.LAW_SEARCH))
    assert "addLawToUserGraph" not in law_no_graph and "searchUserDocuments" not in law_no_graph
    law_graph = tools_for(BrainRequest(message="x", graph_id="g"), plan(T.LAW_SEARCH))
    assert {"addLawToUserGraph", "flagArticleConflict"} <= set(law_graph)
    assert "linkDocumentClauseToArticle" not in law_graph
    audit = tools_for(BrainRequest(message="x", graph_id="g", document_id="d"), plan(T.DOCUMENT_AUDIT))
    assert {"searchUserDocuments", "linkDocumentClauseToArticle", "flagDocumentArticleConflict"} <= set(audit)
    for names in (advice, law_graph, audit):
        assert not {"classifyIntent", "verifyClaims", "extractLegalEntities"} & set(names)


def test_registry_subset_schemas():
    from pydantic import BaseModel

    class A(BaseModel):
        q: str
    r = ToolRegistry()
    for n in ("a", "b", "c"):
        r.register(n, n, A, lambda a: None)
    assert [s["function"]["name"] for s in r.schemas(["c", "a", "zzz"])] == ["c", "a"]
    assert len(r.schemas()) == 3 and r.schemas([]) == []


def test_history_trimmed_newest_first():
    hist = [HistoryEntry(role="user" if i % 2 else "assistant", content=f"m{i} " + "x" * 1500) for i in range(20)]
    out = trim_history(hist)
    assert len(out) <= 8 and sum(len(m["content"]) for m in out) <= MAX_HISTORY_CHARS
    assert out[-1]["content"].startswith("m19")
    assert trim_history([HistoryEntry(role="user", content="y" * 5000)])[0]["content"].endswith("…")


def test_few_shot_only_on_first_turn():
    mb = MessageBuilder("sys", [("u", "a")])
    assert [m["content"] for m in mb.chat("q", [])] == ["sys", "u", "a", "q"]
    follow = mb.chat("q2", [HistoryEntry(role="user", content="q"), HistoryEntry(role="assistant", content="ans")])
    assert [m["content"] for m in follow] == ["sys", "q", "ans", "q2"]


def test_context_budget_cuts_tail_chunks():
    chunks = [RetrievalChunk(text=f"фрагмент {i} " + "т" * 3000, document_id="d", chunk_index=i) for i in range(20)]
    ctx = DocumentContextFormatter().format(chunks)
    assert len(ctx.text) <= CONTEXT_BUDGET_CHARS + 500
    assert 4 <= len(ctx.citations) < 20
