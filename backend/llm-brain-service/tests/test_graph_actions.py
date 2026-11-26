from app.schemas import BrainRequest
from tests.test_graph_support import make_container


async def test_add_law_codes_returns_only_added():
    c = make_container()
    c.graph.fail_add = {"BAD"}
    added = await c.f.actions.add_law_codes("g", ["ТК РК", "BAD", " ", "ТК РК", "ГК РК"], "RK")
    assert added == ["ТК РК", "ГК РК"]
    assert [a[1] for a in c.graph.added] == ["ТК РК", "ГК РК"]


async def test_find_and_add_limits_and_reports():
    c = make_container()
    r = await c.f.actions.find_and_add_laws("g", "трудовой кодекс", "RK", 50)
    assert r.linked == 10 and len(r.law_codes) == 10
    assert r.report.startswith("Найдено и добавлено в граф:")


async def test_link_request_subject_prefers_voice_and_links_laws():
    c = make_container()
    req = BrainRequest(message="свяжи", graph_id="g", document_id="d", voice_id="v")
    r = await c.f.actions.link_request_subject_to_laws(req, 3)
    assert r.linked == 3
    assert all(kind == "voice" for kind, *_ in c.graph.links)
    assert r.report.startswith("**Голосовое сообщение «a.ogg»**")


async def test_link_request_without_subject():
    c = make_container()
    r = await c.f.actions.link_request_subject_to_laws(BrainRequest(message="x", graph_id="g"))
    assert r.linked == 0 and "не указан ни один объект" in r.report
