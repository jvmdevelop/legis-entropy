from fastapi.testclient import TestClient

from app.analysis.compare import MAX_CHARS_PER_SIDE
from tests.test_analysis_fakes import FakeDocs, FakeGraph, FakeLLM, KeywordEmbedder, make_app, make_container


def test_compare_truncation_user_priority_and_alignment():
    long_doc = "1. Арендная плата вносится ежемесячно.\n2. Срок аренды один год.\n" + "я" * 7000
    docs = FakeDocs({"d1": long_doc})
    graph = FakeGraph({"ГК РК": {"law": {"title": "Гражданский кодекс", "summary": "Арендная плата вносится в срок"}}})
    llm = FakeLLM()
    c = make_container(llm=llm, docs=docs, graph=graph, embedder=KeywordEmbedder(["аренд", "срок"]))
    client = TestClient(make_app(c))
    r = client.post("/api/brain/compare", headers={"X-User-Id": "hdr"},
                    json={"leftKind": "DOCUMENT", "leftId": "d1", "leftLabel": "Договор",
                          "rightKind": "LAW", "rightId": "ГК РК", "userId": "body"})
    assert r.status_code == 200 and r.json() == {"content": "diff", "tokens": 7}
    assert docs.calls == [("d1", "hdr")]
    p = llm.prompts[0]
    assert "=== ДОКУМЕНТ A: документ Договор ===" in p and "=== ДОКУМЕНТ B: закон ГК РК ===" in p
    left = p.split("=== ДОКУМЕНТ A: документ Договор ===\n", 1)[1].split("\n\n[...текст обрезан]", 1)[0]
    assert len(left) == MAX_CHARS_PER_SIDE
    assert "СОПОСТАВЛЕННЫЕ ПУНКТЫ" in p and "A[1]" in p


def test_compare_header_user_and_fallbacks():
    docs = FakeDocs({})
    llm = FakeLLM()
    client = TestClient(make_app(make_container(llm=llm, docs=docs)))
    client.post("/api/brain/compare", headers={"X-User-Id": "hdr"},
                json={"leftKind": "DOCUMENT", "leftId": "x", "rightKind": "LAW", "rightId": "", "rightLabel": "ТК"})
    assert docs.calls == [("x", "hdr")]
    p = llm.prompts[0]
    assert "[Текст недоступен: текст документа пуст]" in p and "[Текст недоступен для «ТК»: ID не указан]" in p
    assert "СОПОСТАВЛЕННЫЕ ПУНКТЫ" not in p
