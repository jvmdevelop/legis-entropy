import json

from tests.test_graph_support import CONTRA, FakeNli, make_container

TOOL_NAMES = ["searchLaws", "searchUserDocuments", "getLawGraph", "findRelatedLaws", "addLawToUserGraph",
              "findAndAddRelatedLawsToGraph", "findArticle", "searchArticles", "linkDocumentClauseToArticle",
              "flagArticleConflict", "flagDocumentArticleConflict", "linkDocumentToLawsInGraph"]


def test_registers_exactly_the_12_tools_with_camelcase_schemas():
    c = make_container()
    assert c.tools.names() == TOOL_NAMES
    schemas = {s["function"]["name"]: s["function"] for s in c.tools.schemas()}
    assert "graphId" in schemas["addLawToUserGraph"]["parameters"]["properties"]
    assert {"codeA", "numberA", "codeB", "numberB"} <= set(schemas["flagArticleConflict"]["parameters"]["properties"])
    assert schemas["searchLaws"]["description"].startswith("Поиск в базе нормативно-правовых актов")
    assert c.f.actions is not None and len(c.handlers._handlers) == 4


async def test_article_conflict_not_flagged_without_nli_contradiction():
    c = make_container()
    out = await c.tools.call("flagArticleConflict", {"graphId": "g", "codeA": "ТК РК", "numberA": "1",
                                                     "codeB": "ГК РК", "numberB": "2", "reason": "коллизия"})
    assert "не подтверждён" in out
    assert c.situations.article_conflicts == []


async def test_article_conflict_flagged_when_nli_confirms():
    c = make_container(nli=FakeNli(CONTRA))
    out = await c.tools.call("flagArticleConflict", {"graphId": "g", "codeA": "ТК РК", "numberA": "1",
                                                     "codeB": "ГК РК", "numberB": "2", "reason": "коллизия"})
    assert out.startswith("Конфликт зафиксирован")
    assert c.situations.article_conflicts[0]["confidence"] == 0.9


async def test_document_conflict_requires_nli_contradiction():
    args = {"graphId": "g", "documentId": "d", "lawCode": "ТК РК", "articleNumber": "107", "clauseRef": "п. 3.2"}
    c = make_container()
    assert "не подтверждён" in await c.tools.call("flagDocumentArticleConflict", args)
    assert c.situations.doc_conflicts == []
    c = make_container(nli=FakeNli(CONTRA))
    assert (await c.tools.call("flagDocumentArticleConflict", args)).startswith("Конфликт зафиксирован")
    assert c.situations.doc_conflicts[0]["clauseRef"] == "п. 3.2"


async def test_find_article_extracts_number_with_ner():
    c = make_container()
    out = await c.tools.call("findArticle", {"code": "ТК РК", "number": "ст. 107"})
    assert out.startswith("**Ст. 107 ТК РК")


async def test_link_clause_uses_model_confidence():
    c = make_container()
    out = await c.tools.call("linkDocumentClauseToArticle", {
        "graphId": "g", "documentId": "d", "lawCode": "ТК РК", "articleNumber": "107", "clauseRef": "п. 5",
        "documentSnippet": "Сверхурочная работа оплачивается в двойном размере.", "confidence": 0.1})
    assert out.startswith("Привязан п. 5")
    assert c.graph.clause_links[0]["confidence"] > 0.9


async def test_search_laws_reranks_and_add_tools_report():
    c = make_container()
    chunks = json.loads(await c.tools.call("searchLaws", {"query": "трудовой договор оплата труда"}))
    assert chunks[0]["text"] == "трудовой договор и оплата труда"
    out = await c.tools.call("addLawToUserGraph", {"graphId": "g", "code": "ТК РК"})
    assert out == "Закон ТК РК добавлен в граф."


async def test_find_article_short_code_and_law_service_fallback():
    c = make_container()
    seen = []

    async def failing_article(code, number, country):
        seen.append(code)
        raise RuntimeError("graphs-service: 500")

    async def lookup(code, numbers, country, max_chars=None):
        return [{"articleNumber": "36", "articleTitle": "Условие об испытательном сроке", "excerpt": "не более трёх"}]

    c.graph.article = failing_article
    c.laws.lookup_articles = lookup
    out = await c.tools.call("findArticle", {"code": "ТК", "number": "36"})
    assert seen == ["ТК РК"]
    assert out.startswith("**Ст. 36 ТК РК — Условие об испытательном сроке**") and "не более трёх" in out
