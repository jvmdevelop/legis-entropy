import json

from app.draft.service import DraftDocument
from app.pipeline.types import ContextPlan, RetrievalMode, TaskType
from app.schemas import BrainRequest
from tests.test_graph_support import FakeLLM, make_container

PLAN = ContextPlan(TaskType.DOCUMENT_DRAFT, RetrievalMode.NONE)
REQ = BrainRequest(message="Составь иск о взыскании зарплаты. Истец Иванов.", user_id="u", graph_id="g")


async def test_draft_stream_event_sequence():
    doc = DraftDocument(title="Иск", body="# Иск\n{{фио_истца}}", variables={"фио_истца": "Иванов"})
    c = make_container(llm=FakeLLM(structured=doc))
    h = c.handlers.resolve(TaskType.DOCUMENT_DRAFT)
    events = [e async for e in h.stream(REQ, PLAN)]
    steps = [json.loads(e.data)["step"] for e in events if e.event == "thinking"]
    assert steps == ["draft-laws", "draft-situation", "draft-link", "draft-generate", "draft-template", "draft-save"]
    assert [e.event for e in events[-2:]] == ["document-draft-result", None]
    result = json.loads(events[-2].data)
    assert result["templateId"] == "tpl-1" and result["generatedDocId"] == "gen-1"
    assert result["situationId"] == "sit-1" and len(result["lawCodes"]) == 5
    assert events[-1].data.startswith("**Документ подготовлен.**")
    assert c.templates.created[0]["body"] == doc.body and c.templates.created[0]["kind"] == "USER"
    assert c.templates.rendered[0][2] == {"фио_истца": "Иванов"}
    assert c.situations.generated == [("sit-1", "gen-1", "Составь иск о взыскании зарплаты")]
    assert len(c.situations.law_links) == 5


async def test_draft_falls_back_to_plain_completion_and_errors_stream():
    c = make_container(llm=FakeLLM(complete="# Документ", structured=None))
    r = await c.handlers.resolve(TaskType.DOCUMENT_DRAFT).handle(REQ, PLAN)
    assert r.content.startswith("**Документ подготовлен.**")
    assert c.templates.created[0]["body"] == "# Документ"

    c = make_container(llm=FakeLLM(complete=RuntimeError("boom"), structured=None))
    events = [e async for e in c.handlers.resolve(TaskType.DOCUMENT_DRAFT).stream(REQ, PLAN)]
    assert events[-1].event == "error" and events[-1].data == "Ошибка при подготовке документа: boom"
