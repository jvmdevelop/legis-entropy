from app.legal.glossary import GlossaryService, parse_definitions
from app.legal.hierarchy import level_of, resolve
from app.playbooks.service import TEMPLATES, PlaybookChecker, PlaybookStore, _Verdict, _Verdicts
from app.schemas import Playbook, PlaybookRule
from app.structure.classify import classify
from app.structure.parser import Page, parse
from tests.test_analysis_fakes import FakeLLM

LEASE = ("ДОГОВОР АРЕНДЫ\n\n1. ПРЕДМЕТ ДОГОВОРА\n\n"
         "1.1. Арендодатель передаёт Арендатору во временное владение помещение.\n\n"
         "3. ОПЛАТА\n\n3.1. Арендная плата вносится в течение 45 календарных дней после выставления счета.\n\n"
         "6. РАСТОРЖЕНИЕ\n\n6.1. Арендодатель вправе расторгнуть договор в одностороннем порядке.\n"
         "6.2. Споры разрешаются в суде по месту нахождения Арендодателя.\n")


async def _structure(text=LEASE, pages=None):
    st = parse(text, pages)
    await classify(st.clauses)
    return st


async def test_structure_sections_clauses_pages():
    cut = LEASE.index("3. ОПЛАТА")
    st = await _structure(pages=[Page(1, 0, cut), Page(2, cut, len(LEASE))])
    assert [s.number for s in st.sections] == ["1", "3", "6"]
    assert [(c.number, c.section, c.page) for c in st.clauses] == [("1.1", "1", 1), ("3.1", "3", 2), ("6.1", "6", 2),
                                                                  ("6.2", "6", 2)]
    c = st.clause("6.1")
    assert [t.code for t in c.types] == ["TERMINATION"] and (c.modality, c.party) == ("RIGHT", "Арендодатель")
    assert "SUBJECT" in [t.code for t in st.clause("1.1").types]
    assert "DISPUTES" in [t.code for t in st.clause("6.2").types]


async def test_playbook_structured_rules():
    st = await _structure()
    pb = Playbook(id="p", name="x", rules=[
        PlaybookRule(id="a", kind="max_days", clause_type="PAYMENT", max_days=30, text="оплата <= 30 дней"),
        PlaybookRule(id="b", kind="required", clause_type="CONFIDENTIALITY", text="нужна конфиденциальность"),
        PlaybookRule(id="c", kind="forbidden", clause_type="TERMINATION", party="Арендодатель", modality="RIGHT",
                     text="нет одностороннего расторжения арендодателем"),
        PlaybookRule(id="d", kind="required", clause_type="DISPUTES", text="есть споры")])
    res = {r.rule_id: r for r in (await PlaybookChecker().check(pb, st, LEASE)).results}
    assert res["a"].status == "VIOLATED" and res["a"].clause == "3.1" and "45" in res["a"].explanation
    assert res["b"].status == "VIOLATED"
    assert res["c"].status == "VIOLATED" and res["c"].clause == "6.1"
    assert res["d"].status == "OK"


async def test_playbook_text_rule_quote_validated():
    st = await _structure()
    llm = FakeLLM(structured=lambda s, u, schema: _Verdicts(items=[
        _Verdict(rule_id="t", status="violated", clause="6.1", quote="Арендодатель вправе расторгнуть договор",
                 explanation="одностороннее расторжение"),
        _Verdict(rule_id="u", status="OK", clause="3.1", quote="такой цитаты в договоре нет", explanation="ок")]))
    pb = Playbook(id="p", name="x", rules=[PlaybookRule(id="t", text="без одностороннего расторжения"),
                                           PlaybookRule(id="u", text="правило 2")])
    res = {r.rule_id: r for r in (await PlaybookChecker(llm).check(pb, st, LEASE)).results}
    assert res["t"].status == "VIOLATED" and res["t"].quote and res["t"].page == 1
    assert res["u"].quote is None


async def test_playbook_store_memory_and_templates():
    store = PlaybookStore("")
    saved = await store.save("u1", Playbook(name="Мои", rules=[PlaybookRule(text="x", kind="weird",
                                                                             clause_type="NOPE")]))
    assert saved.id and saved.rules[0].kind == "text" and saved.rules[0].clause_type is None
    assert [p.id for p in await store.list("u1")] == [saved.id] and await store.list("u2") == []
    assert (await store.get("u1", TEMPLATES[0].id)).name == TEMPLATES[0].name
    assert await store.delete("u1", saved.id) and await store.list("u1") == []


def test_hierarchy():
    assert (level_of("Конституция РК"), level_of("ТК РК"), level_of("Закон РК №405-II")) == (1, 4, 5)
    r = resolve("Закон РК №287-VIII", "ТК РК")
    assert r.prevails == "ТК РК"
    assert resolve("ТК РК", "ГК РК").prevails is None


async def test_glossary_relevant_terms():
    class Laws:
        async def article_titles(self, code, country):
            return [{"articleNumber": "1", "articleTitle": "Основные понятия, используемые в настоящем Кодексе"}]

        async def lookup_articles(self, code, numbers, country, max_chars=None):
            return [{"excerpt": "1) сменная работа – работа в две, три или четыре смены; "
                                "2) минимальный размер месячной заработной платы – гарантированный минимум выплат; "
                                "3) гражданская служба – деятельность служащих."}]

    g = GlossaryService(Laws())
    terms = await g.relevant("Работодатель ввёл сменную работу, а заработная плата ниже минимального размера в месяц")
    assert [t.term for t in terms] == ["минимальный размер месячной заработной платы", "сменная работа"]
    assert len(parse_definitions("1) термин – слишком", "X", "1")) == 0
