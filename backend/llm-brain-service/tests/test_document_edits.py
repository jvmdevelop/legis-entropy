from fastapi.testclient import TestClient

from app.edits.service import DocumentEditService, _Edit, _Edits, article_swap, requisites_swap
from app.schemas import AuditFinding, EditProposal
from tests.test_analysis_fakes import FakeDocs, FakeLLM, make_app, make_container

TK = "Трудового кодекса Республики Казахстан"
DOC = (f"трудовой договор\n\n"
       f"3.1. В соответствии со статьёй 49 {TK} устанавливается испытательный срок два месяца.\n"
       f"\n6.1. В соответствии со статьёй 87 {TK} предоставляется отпуск не менее двадцати одного календарного дня.\n"
       "10.2. Споры — по Гражданскому процессуальному кодексу РК от 31.10.2015 № 377-VI.\n")


def F(clause, verdict, cited="", correct="", source="rule", law="ТК РК", **kw):
    return AuditFinding(clause=clause, law_code=law, cited_article=cited, verdict=verdict, correct_article=correct,
                        explanation="x", source=source, **kw)


def test_number_swaps_keep_wording():
    assert article_swap(F("3.1", "WRONG_ARTICLE", "49", "36"), DOC.splitlines()[2]) == ("статьёй 49", "статьёй 36")
    assert requisites_swap(F("10.2", "WRONG_REQUISITES", "377-VI", "377-V", law="ГПК РК"),
                           DOC.splitlines()[5]) == ("№ 377-VI", "№ 377-V")
    assert article_swap(F("3.1", "WRONG_ARTICLE", "49", ""), DOC) is None


async def test_proposals_rules_deterministic_violation_by_llm():
    llm = FakeLLM(structured=lambda s, u, schema: _Edits(edits=[
        _Edit(i=0, original="не менее двадцати одного календарного дня",
              replacement="не менее двадцати четырёх календарных дней", reason="ст. 88 ТК РК — 24 дня"),
        _Edit(i=0, original="такого текста нет", replacement="y")]))
    svc = DocumentEditService(None, FakeDocs(), llm)
    findings = [F("3.1", "WRONG_ARTICLE", "49", "36"), F("6.1", "WRONG_ARTICLE", "87", "88"),
                F("6.1", "VIOLATION", "87", "88"), F("10.2", "WRONG_REQUISITES", "377-VI", "377-V", law="ГПК РК"),
                F("9.9", "WRONG_ARTICLE", "1", "2"),
                F("3.1", "WRONG_ARTICLE", "49", "50", source="llm"),
                F("6.1", "OK", "87")]
    ps = await svc.proposals(DOC, findings)
    got = [(p.clause, p.original, p.replacement, p.source) for p in ps]
    assert ("3.1", "статьёй 49", "статьёй 36", "rule") in got
    assert ("6.1", "статьёй 87", "статьёй 88", "rule") in got
    assert ("10.2", "№ 377-VI", "№ 377-V", "rule") in got
    assert ("6.1", "не менее двадцати одного календарного дня", "не менее двадцати четырёх календарных дней",
            "llm") in got
    assert all(p.original != "такого текста нет" for p in ps)
    assert len({p.id for p in ps}) == len(ps)


def test_apply_to_text_skips_missing_and_overlaps():
    p1 = EditProposal(id="a", clause="3.1", original="статьёй 49", replacement="статьёй 36")
    p2 = EditProposal(id="b", clause="3.1", original="статьёй 49 Трудового", replacement="ст. 36 Трудового")
    p3 = EditProposal(id="c", clause="6.1", original="нет такого", replacement="z")
    out, applied, skipped = DocumentEditService.apply_to_text(DOC, [p1, p2, p3])
    assert "статьёй 36 Трудового" in out and "статьёй 49" not in out
    assert applied == ["a"] and {s.id for s in skipped} == {"b", "c"}


def test_api_apply_creates_new_document_and_suggest():
    docs = FakeDocs({"d1": DOC})
    c = make_container(docs=docs)
    client = TestClient(make_app(c))
    body = {"proposals": [{"id": "a", "clause": "3.1", "original": "статьёй 49", "replacement": "статьёй 36"}]}
    r = client.post("/api/brain/documents/d1/edits/apply", json=body, headers={"X-User-Id": "u1"})
    assert r.status_code == 200
    j = r.json()
    assert j["documentId"] == "copy-1" and j["applied"] == ["a"] and j["fileName"].endswith(".docx")
    assert docs.edited == ("d1", "u1", [{"original": "статьёй 49", "replacement": "статьёй 36", "occurrence": 0}],
                           None)
    docs.edited_copy_fails = True
    j = client.post("/api/brain/documents/d1/edits/apply", json=body, headers={"X-User-Id": "u1"}).json()
    assert j["documentId"] == "new-doc-1" and j["fileName"] == "Договор (исправлено).txt"
    user, name, text, ctype = docs.uploaded
    assert user == "u1" and "статьёй 36" in text and ctype.startswith("text/plain")
    assert client.post("/api/brain/documents/d1/edits/apply", json=body).status_code == 400
    r = client.post("/api/brain/documents/d1/edits/suggest", headers={"X-User-Id": "u1"})
    assert r.status_code == 200
    ps = r.json()["proposals"]
    assert any(p["original"] == "№ 377-VI" and p["replacement"] == "№ 377-V" for p in ps)


async def test_chat_audit_emits_edit_proposals_event():
    import json
    from app.pipeline.handlers.default import EDITS_EVENT, DefaultBrainHandler
    from app.pipeline.types import ContextPlan, Profession, RetrievalMode, TaskType
    from app.schemas import BrainRequest

    class Edits:
        async def suggest_for_text(self, text):
            assert text == DOC
            return [EditProposal(id="a", clause="3.1", original="статьёй 49", replacement="статьёй 36")], "s"

    class Broken:
        async def suggest_for_text(self, text):
            raise RuntimeError("audit down")

    req = BrainRequest(message="проверь договор", document_id="d1", user_id="u1")
    plan = lambda t: ContextPlan(t, RetrievalMode.USER_DOCUMENT, [Profession.LABOR_LAWYER])  # noqa: E731
    h = DefaultBrainHandler(None, None, None, None, None, edits=Edits(), documents=FakeDocs({"d1": DOC}))
    ev = await h._finish_edits(h._start_edits(req, plan(TaskType.DOCUMENT_AUDIT)))
    assert ev.event == EDITS_EVENT
    data = json.loads(ev.data)
    assert data["documentId"] == "d1" and data["proposals"][0]["replacement"] == "статьёй 36"
    assert h._start_edits(req, plan(TaskType.DOCUMENT_QA)) is None
    h.edits = Broken()
    assert await h._finish_edits(h._start_edits(req, plan(TaskType.DOCUMENT_AUDIT))) is None


def test_keep_end_punctuation():
    from app.edits.service import keep_end_punctuation
    assert keep_end_punctuation("Законом № 405-II.", "Социальным кодексом РК") == "Социальным кодексом РК."
    assert keep_end_punctuation("статьёй 49", "статьёй 36") == "статьёй 36"
    assert keep_end_punctuation("дня.", "дней.") == "дней."


def test_occurrence_index_for_repeated_fragment():
    from app.edits.service import occurrence
    t = "3.1. статьёй 49 ...\n9.2. статьёй 49 ..."
    assert occurrence(t, "статьёй 49", t.find("статьёй 49")) == 0
    assert occurrence(t, "статьёй 49", t.rfind("статьёй 49")) == 1
