from app.pipeline.context import DocumentContextFormatter, to_chunks
from app.pipeline.retrieval import LawRetrievalStrategy, QueryRewriter, rewrite_all
from app.pipeline.retrieval.rewriter import MAX_SUB_QUESTIONS, _Decomposition
from app.pipeline.retrieval.strategies import MAX_MERGED_HITS, PER_QUESTION_HITS
from app.pipeline.types import ContextPlan, RetrievalMode as R, TaskType as T
from app.schemas import BrainRequest
from tests.test_pipeline_fakes import FakeDocs, FakeGraph, FakeLLM, FakeLaws, build, law_hit


def test_rewriter_rules():
    assert rewrite_all("Привет, расскажи что такое трудовой договор") == ["привет, трудовой договор"]
    qs = rewrite_all("какой срок исковой давности по закону? какие штрафы за просрочку налога? ок?")
    assert len(qs) == 2 and all(len(q) >= 15 for q in qs)
    many = "?".join(f"вопрос номер {i} про трудовой кодекс" for i in range(20))
    assert len(rewrite_all(many)) == MAX_SUB_QUESTIONS


async def test_llm_decomposition_and_hyde_merged():
    llm = FakeLLM(structured_result=_Decomposition(subQuestions=["срок исковой давности РК", "коротко"],
                                                   hypothetical="Общий срок исковой давности составляет три года."))
    qs = await QueryRewriter(llm, min_llm_length=10).rewrite("какой срок исковой давности по долгу")
    assert "срок исковой давности РК" in qs and "коротко" not in qs
    assert qs[-1].startswith("Общий срок") and len(qs) <= MAX_SUB_QUESTIONS


def test_fragment_limit_and_citation_ids():
    ctx = DocumentContextFormatter().format(to_chunks([law_hit("x" * 5000, article=88), {"text": "  "}]))
    assert len(ctx.citations) == 1 and ctx.citations[0].id == "doc-law-1-chunk-0"
    assert "=== [Статья 88 | 414-V] ===" in ctx.text and "x" * 2501 not in ctx.text
    assert DocumentContextFormatter().format([]).unavailable


async def test_law_multi_question_limits_and_graph_hops():
    hits = [law_hit(f"норма {i} о трудовом договоре", code=f"C{i}", doc="d", idx=i) for i in range(10)]
    laws = FakeLaws(default_hits=hits)
    graph = FakeGraph({"C0": [{"code": "R1", "title": "Связанный"}], "R1": [{"code": "R2"}], "R2": [{"code": "R3"}]})
    s = LawRetrievalStrategy(laws, graph, DocumentContextFormatter(), QueryRewriter(None))
    queries = [f"подвопрос номер {i} про договор" for i in range(12)]
    merged = await s.search_each(queries)
    assert len(merged) <= MAX_MERGED_HITS and len(merged) <= PER_QUESTION_HITS * len(hits)
    laws2 = FakeLaws(hits_by_query={q: [law_hit(f"{q} {j}", doc=q, idx=j) for j in range(5)] for q in queries})
    s2 = LawRetrievalStrategy(laws2, graph, DocumentContextFormatter(), QueryRewriter(None))
    assert len(await s2.search_each(queries)) == MAX_MERGED_HITS
    g = await s.graph_context("q", to_chunks([law_hit("t", code="C0")]))
    assert "• R1 — Связанный" in g and "R2" in g and "R3" not in g


async def test_user_document_full_text_only_for_summary_under_limit():
    _, h = build(docs=FakeDocs(text="полный текст", chunks=[{"text": "фрагмент", "chunkIndex": 1}]))
    req = BrainRequest(message="кратко", document_id="d")
    ctx = await h.router.retrieve(req, ContextPlan(T.SUMMARY, R.USER_DOCUMENT))
    assert ctx.text == "полный текст"
    _, h = build(docs=FakeDocs(text="x" * 60_001, chunks=[{"text": "фрагмент", "chunkIndex": 1}]))
    ctx = await h.router.retrieve(req, ContextPlan(T.SUMMARY, R.USER_DOCUMENT))
    assert "Фрагмент [chunk-1]" in ctx.text


async def test_document_unavailable_message():
    pipeline, _ = build(docs=FakeDocs(fail_search=True))
    r = await pipeline.answer(BrainRequest(message="что в документе?", document_id="d"))
    assert r.content.startswith("Не удалось обработать прикреплённый документ: Не удалось загрузить контекст")
    assert r.tokens == 0
