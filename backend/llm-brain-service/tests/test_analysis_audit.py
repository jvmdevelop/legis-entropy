import json

from fastapi.testclient import TestClient

from app.analysis.labor import LaborNormChecker, _Conditions, last_number, regex_check
from app.analysis.references import extract_references
from tests.test_analysis_fakes import (FakeDocs, FakeGraph, FakeLLM, FakeLaws, FakeNli, KeywordEmbedder, make_app,
                                       make_container)


def by_verdict(audit, verdict):
    return [f for f in audit.findings if f.verdict == verdict]


async def test_repealed_act_from_registry_when_graph_unavailable():
    c = make_container()
    a = await c.audit.audit("2.1. Отношения регулируются Законом РК № 493-I «О труде».")
    [f] = by_verdict(a, "REPEALED")
    assert f.law_code == "493-I" and f.clause == "2.1" and "утратил силу" in f.explanation
    assert a.problems == 1 and "🗑 **Акт утратил силу** · п. 2.1 · 493-I" in a.markdown
    assert a.summary.startswith("Проверено ссылок: 1.")


async def test_repealed_status_from_graph_wins_over_registry():
    graph = FakeGraph({"493-I": {"law": {"status": "ACTIVE"}},
                       "77-VI": {"law": {"title": "Закон о чём-то", "status": "REPEALED"}}})
    c = make_container(graph=graph)
    a = await c.audit.audit("1. См. закон № 493-I и закон № 77-VI.")
    assert [f.law_code for f in by_verdict(a, "REPEALED")] == ["77-VI"]


def _rent_laws():
    return FakeLaws(
        articles={"ГК РК": {"100": {"articleNumber": "100", "articleTitle": "Наследование по закону",
                                    "excerpt": "Наследники призываются к наследованию"}}},
        titles={"ГК РК": [{"articleNumber": "100", "articleTitle": "Наследование"},
                          {"articleNumber": "546", "articleTitle": "Арендная плата"}]},
        search=[{"text": "Статья 546\nАрендная плата вносится в порядке, установленном договором",
                 "metadata": {"lawCode": "ГК РК", "articleNumber": "546", "articleTitle": "Арендная плата"}},
                {"text": "other", "metadata": {"lawCode": "НК РК", "articleNumber": "1"}}])


async def test_wrong_article_by_embeddings_when_llm_offline():
    c = make_container(laws=_rent_laws(), embedder=KeywordEmbedder(["аренд", "плат", "наслед"]))
    a = await c.audit.audit(
        "4.2. Арендная плата вносится ежемесячно согласно ст. 100 Гражданского кодекса РК.\n"
        "4.3. Пеня взыскивается по ст. 999 ГК РК.")
    [w] = by_verdict(a, "WRONG_ARTICLE")
    assert (w.clause, w.law_code, w.cited_article, w.correct_article) == ("4.2", "ГК РК", "100", "546")
    assert w.confidence and w.confidence > 0.9
    [nf] = by_verdict(a, "NOT_FOUND")
    assert nf.cited_article == "999"
    assert "⚠️ **Ошибочная ссылка** · п. 4.2 · ст. 100 ГК РК → правильно: ст. 546" in a.markdown


async def test_report_and_llm_verdicts_are_parsed():
    reply = json.dumps({"findings": [{"clause": "4.2", "lawCode": "ГК РК", "citedArticle": "ст. 100",
                                      "verdict": "WRONG_ARTICLE", "correctArticle": "546", "explanation": "про аренду"}],
                        "okArticles": [], "summary": "x"}, ensure_ascii=False)
    llm = FakeLLM(complete=lambda s, u: "```json\n" + reply + "\n```")
    c = make_container(llm=llm, laws=_rent_laws(), embedder=KeywordEmbedder(["аренд", "плат", "наслед"]))
    a = await c.audit.audit("4.2. Арендная плата вносится согласно ст. 100 ГК РК.")
    report = llm.prompts[0]
    assert "→ ГК РК ст. 100: фактически «Наследование по закону». Начало текста: Наследники" in report
    assert "по смыслу пункта подходит: ГК РК ст. 546 «Арендная плата»: Арендная плата вносится" in report
    assert "Оглавление ГК РК (для подбора правильных статей):\nст. 100 — Наследование; ст. 546 — Арендная плата" in report
    [w] = a.findings
    assert w.cited_article == "100" and w.explanation == "про аренду" and w.confidence is not None


async def test_labor_norm_violation_regex_fallback():
    laws = FakeLaws(articles={"ТК РК": {"36": {"articleNumber": "36", "articleTitle": "Испытательный срок",
                                              "excerpt": "Испытательный срок не более трех месяцев"}}})
    c = make_container(laws=laws, embedder=KeywordEmbedder(["испыт"]))
    a = await c.audit.audit(
        "Трудовой договор\n3.1. Испытательный срок составляет шесть месяцев (ст. 36 ТК РК).")
    [v] = by_verdict(a, "VIOLATION")
    assert (v.clause, v.law_code, v.cited_article, v.correct_article) == ("3.1", "ТК РК", "36", "36")
    assert v.explanation.startswith("В договоре указано 6, а испытательный срок по ст. 36 ТК РК")
    assert not by_verdict(a, "OK")


async def test_nli_contradiction_gives_violation():
    laws = FakeLaws(articles={"ГК РК": {"546": {"articleNumber": "546", "articleTitle": "Арендная плата",
                                               "excerpt": "Арендная плата вносится"}}})
    c = make_container(laws=laws, embedder=KeywordEmbedder(["аренд"]), nli=FakeNli(contradiction=0.95))
    a = await c.audit.audit("5.1. Арендная плата не вносится никогда (ст. 546 ГК РК).")
    assert [f.verdict for f in a.findings] == ["VIOLATION"]


async def test_low_similarity_is_unverified():
    laws = FakeLaws(articles={"ГК РК": {"7": {"articleNumber": "7", "articleTitle": "Иное", "excerpt": "иное"}}})
    c = make_container(laws=laws, embedder=KeywordEmbedder(["аренд", "иное"]))
    a = await c.audit.audit("1.1. Арендатор обязан платить по ст. 7 ГК РК.")
    assert [f.verdict for f in a.findings] == ["UNVERIFIED"] and "Не подтверждено автоматически — 1" in a.summary


async def test_labor_llm_structured_extraction():
    llm = FakeLLM(structured=lambda s, u, schema: _Conditions.model_validate(
        {"conditions": [{"kind": "vacation_days", "value": 18, "clause": 5.2, "citedArticle": "ст. 88"},
                        {"kind": "weekly_hours", "value": 40, "clause": "5.3"}]}))
    [v] = await LaborNormChecker(llm).check("Трудовой договор\n5.2. Отпуск восемнадцать календарных дней.")
    assert (v.clause, v.cited_article, v.correct_article) == ("5.2", "88", "88")


def test_labor_regex_rules_and_number_words():
    assert last_number("составляет двадцать один") == 21 and last_number("до 45") == 45
    text = ("Трудовой договор\n2.1. Ежегодный отпуск — 14 календарных дней.\n"
            "2.2. Рабочая неделя — 48 часов.\n2.3. Испытательный срок 3 месяца.")
    assert [(v.clause, v.correct_article) for v in regex_check(text)] == [("2.1", "88"), ("2.2", "68")]
    assert regex_check("Договор услуг. Отпуск 3 календарных дня") == []


def test_reference_extractor_codes():
    refs = extract_references("согласно ст. 30 и 33 Трудового кодекса РК; ст. 5 настоящего закона; ст. 7 ГК")
    assert [(r.law_code, r.article_number) for r in refs] == [("ТК РК", "30"), ("ТК РК", "33"), ("ГК РК", "7")]


def test_audit_http_status_codes():
    docs = FakeDocs({"missing": RuntimeError("404"), "empty": "  ", "ok": "Договор № 493-I"})
    client = TestClient(make_app(make_container(docs=docs)))
    assert client.post("/api/brain/documents/ok/audit").status_code == 400
    h = {"X-User-Id": "u1"}
    assert client.post("/api/brain/documents/missing/audit", headers=h).status_code == 404
    assert client.post("/api/brain/documents/empty/audit", headers=h).status_code == 422
    r = client.post("/api/brain/documents/ok/audit", headers=h)
    assert r.status_code == 200
    body = r.json()
    assert set(body) == {"summary", "markdown", "problems", "findings"} and body["problems"] == 1
    assert body["findings"][0]["verdict"] == "REPEALED" and "lawCode" in body["findings"][0]
    assert docs.calls[-1] == ("ok", "u1")


def test_audit_empty_document_without_references():
    client = TestClient(make_app(make_container(docs=FakeDocs({"d": "Просто текст"}))))
    assert client.post("/api/brain/documents/d/audit", headers={"X-User-Id": "u"}).json() == \
        {"summary": "", "markdown": "", "problems": 0, "findings": []}
