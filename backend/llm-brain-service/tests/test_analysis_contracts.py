from fastapi.testclient import TestClient

from app.analysis.clauses import MAX_CLAUSE_TEXT, ClauseSegmenter, extract_clauses
from app.analysis.contract_risk import BATCH_SIZE, _RiskItems
from app.analysis.contract_types import ContractTypeService
from tests.test_analysis_fakes import FakeClassifier, FakeLLM, FakeLaws, KeywordEmbedder, ids_in, make_app, make_container

LEVELS = ["VIOLATION", "предупреждение", "COMPLIANT"]


def risk_llm():
    def structured(system, user, schema):
        if schema is _RiskItems:
            return _RiskItems.model_validate(
                [{"clauseId": cid, "riskLevel": LEVELS[i % 3], "lawCitation": "ст. 1 ГК РК" if i % 3 < 2 else "null"}
                 for i, cid in enumerate(ids_in(user))])
        return None
    return FakeLLM(structured=structured)


async def test_risk_scan_limits_batches_and_counters():
    text = "Договор аренды\n" + "\n".join(f"{i}. Арендатор обязуется " + "х" * 500 for i in range(1, 61))
    llm = risk_llm()
    laws = FakeLaws(search=[{"text": "т" * 400, "metadata": {"lawCode": "ГК РК", "articleNumber": "546"}}])
    c = make_container(llm=llm, laws=laws)
    r = await c.contract_risk.scan(text, " ")
    assert r.contract_type == "аренды"
    assert len(r.clauses) == 50 and all(len(x.clause_text) <= MAX_CLAUSE_TEXT + 1 for x in r.clauses)
    batches = [len(ids_in(u)) for name, u in llm.structured_calls if name == "_RiskItems"]
    assert batches == [BATCH_SIZE, BATCH_SIZE, BATCH_SIZE, 5]
    assert (r.total_violations, r.total_warnings, r.total_compliant) == (17, 17, 16)
    assert r.clauses[2].law_citation is None and r.clauses[0].clause_text.startswith("1. Арендатор")
    prompt = llm.structured_calls[0][1]
    assert "ПРИМЕНИМЫЕ НОРМЫ ИЗ БАЗЫ ЗАКОНОВ:\n[ст. 546 ГК РК] " in prompt and "т" * 300 + "…" in prompt
    assert laws.search_calls == 2


async def test_risk_scan_llm_failure_falls_back_to_compliant_and_missing_clauses():
    c = make_container(llm=FakeLLM(), embedder=KeywordEmbedder([("зарплат", "деньги")]))
    r = await c.contract_risk.scan("Трудовой договор\n1. Работник выполняет поручения.\n"
                                                "2. Работодатель платит деньги раз в месяц.", "RK")
    assert r.contract_type == "трудовой"
    assert r.total_compliant == 2 and r.total_violations == 0
    names = [m.required_clause for m in r.missing_clauses]
    assert "Место выполнения работы" in names and "Должность / трудовая функция" in names
    assert "Размер и порядок выплаты заработной платы" not in names
    assert "Режим рабочего времени" in names


def test_regex_clause_extractor_limits():
    text = "\n".join(f"п. {i}) Текст пункта номер {i}" for i in range(1, 80))
    cl = extract_clauses(text)
    assert len(cl) == 50 and cl[0].id == "1" and cl[-1].id == "50"


async def test_llm_segmentation_fallback_when_regex_finds_less_than_two():
    from app.analysis.clauses import _Segmentation
    llm = FakeLLM(structured=lambda s, u, schema: _Segmentation.model_validate(
        {"clauses": [{"id": 1, "text": "Стороны договорились"}, {"id": 2, "text": "Оплата " * 100}]}))
    cl = await ClauseSegmenter(llm).segment("Стороны договорились. Оплата производится ежемесячно.")
    assert [c.id for c in cl] == ["1", "2"] and len(cl[1].text) <= MAX_CLAUSE_TEXT + 1


async def test_contract_type_rules_then_classifier():
    svc = ContractTypeService(FakeClassifier("подряда", 0.9))
    assert await svc.detect("Работодатель и работник") == "трудовой"
    assert await svc.detect("Стороны договорились о ремонте") == "подряда"
    assert await ContractTypeService(FakeClassifier("подряда", 0.1)).detect("ремонт") == "иной"


def test_risk_scan_http_status():
    client = TestClient(make_app(make_container(llm=risk_llm())))
    assert client.post("/api/brain/contracts/risk-scan", json={"text": "  "}).status_code == 400
    assert client.post("/api/brain/contracts/risk-scan", json={}).status_code == 400
    r = client.post("/api/brain/contracts/risk-scan", json={"text": "1. Поставщик передаёт товар.\n2. Покупатель платит."})
    assert r.status_code == 200
    body = r.json()
    assert set(body) == {"contractType", "clauses", "missingClauses", "totalViolations", "totalWarnings",
                         "totalCompliant"}
    assert body["contractType"] == "поставки" and body["clauses"][0]["clauseId"] == "1"
    assert set(body["clauses"][0]) == {"clauseId", "clauseText", "riskLevel", "lawCitation", "reason"}
