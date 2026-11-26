import pytest

from app.pipeline.intent import IntentClassifier, classify_depth, classify_rules
from app.pipeline.profession import ProfessionClassifier
from app.pipeline.types import Profession, RetrievalMode as R, TaskType as T
from app.schemas import BrainRequest
from tests.test_pipeline_fakes import FakeClassifier, build


def plan(msg, **kw):
    return classify_rules(BrainRequest(message=msg, **kw)).plan


@pytest.mark.parametrize("msg,kw,task,mode", [
    ("Составь исковое заявление о взыскании долга", {}, T.DOCUMENT_DRAFT, R.LAW),
    ("помогите написать претензию", {}, T.DOCUMENT_DRAFT, R.LAW),
    ("как написать претензию?", {}, T.GENERAL_LEGAL_ADVICE, R.NONE),
    ("Проведи глубокий анализ графа", {}, T.GRAPH_DEEP_ANALYSIS, R.NONE),
    ("свяжи мой документ с законами", {"document_id": "d"}, T.GRAPH_LINK_DOCUMENT, R.NONE),
    ("добавь в граф трудовой кодекс", {}, T.GRAPH_ADD_LAWS, R.NONE),
    ("кратко перескажи", {"document_id": "d"}, T.SUMMARY, R.USER_DOCUMENT),
    ("кратко про закон о браке", {}, T.SUMMARY, R.LAW),
    ("сравни договор с законом", {"document_id": "d"}, T.DOCUMENT_AUDIT, R.HYBRID_LEGAL),
    ("проверь риски", {"document_id": "d"}, T.DOCUMENT_AUDIT, R.USER_DOCUMENT),
    ("законно ли это, можно ли так", {"document_id": "d"}, T.DOCUMENT_QA, R.HYBRID_LEGAL),
    ("какой срок оплаты в договоре?", {"document_id": "d"}, T.DOCUMENT_QA, R.USER_DOCUMENT),
    ("какая статья ТК регулирует отпуск", {}, T.LAW_SEARCH, R.LAW),
    ("что делать", {}, T.GENERAL_LEGAL_ADVICE, R.NONE),
])
def test_rules(msg, kw, task, mode):
    p = plan(msg, **kw)
    assert (p.task, p.retrieval) == (task, mode)


def test_depth():
    assert classify_depth("максимально глубокий анализ") == 3
    assert classify_depth("глубокий анализ") == 2


async def test_ml_only_for_fallthrough():
    ml = FakeClassifier("LAW_SEARCH", 0.9)
    ic = IntentClassifier(ml, 0.7)
    p = await ic.classify(BrainRequest(message="меня уволили без причины, помогите разобраться"))
    assert p.task == T.LAW_SEARCH and p.retrieval == R.LAW and ml.calls == 1
    ml.calls = 0
    p = await ic.classify(BrainRequest(message="какая статья про отпуск"))
    assert p.task == T.LAW_SEARCH and ml.calls == 0


async def test_ml_low_confidence_keeps_rule_result():
    p = await IntentClassifier(FakeClassifier("LAW_SEARCH", 0.4), 0.7).classify(BrainRequest(message="привет"))
    assert (p.task, p.retrieval) == (T.GENERAL_LEGAL_ADVICE, R.NONE)


async def test_graph_task_without_graph_falls_back():
    pipeline, _ = build()
    p = await pipeline.resolve_plan(BrainRequest(message="добавь в граф трудовой кодекс"))
    assert (p.task, p.retrieval) == (T.GENERAL_LEGAL_ADVICE, R.NONE)
    p = await pipeline.resolve_plan(BrainRequest(message="добавь в граф трудовой кодекс", graph_id="g"))
    assert p.task == T.GRAPH_ADD_LAWS


async def test_profession_rules_and_ml_threshold():
    pc = ProfessionClassifier(FakeClassifier("LABOR_LAWYER", 0.6), 0.5)
    assert (await pc.classify("развод и алименты"))[0] == Profession.FAMILY_LAWYER
    assert await pc.classify("вопрос про отношения с начальством") == (Profession.LABOR_LAWYER,)
    low = ProfessionClassifier(FakeClassifier("LABOR_LAWYER", 0.3), 0.5)
    assert await low.classify("вопрос про отношения с начальством") == (Profession.GENERAL_LAWYER,)
