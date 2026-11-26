import json

from app.analysis.labor import qualitative_check
from app.analysis.repealed import REGISTRY
from app.analysis.requisites import check_requisites
from app.analysis.topics import expected_article
from tests.test_analysis_fakes import FakeLLM, FakeLaws, make_container

TK = "Трудового кодекса Республики Казахстан"


def test_requisites_wrong_code_number():
    [i] = check_requisites("10.2. В порядке, установленном Гражданским процессуальным кодексом РК "
                           "от 31.10.2015 года № 377-VI.")
    assert (i.code, i.cited_number) == ("ГПК РК", "377-VI") and "377-V" in i.requisites


def test_requisites_correct_superseded_and_foreign_numbers_ignored():
    assert check_requisites("Гражданским процессуальным кодексом от 31 октября 2015 года № 377-V") == []
    assert check_requisites("Трудовым кодексом РК от 15 мая 2007 года № 251-III") == []
    assert check_requisites("Трудовым кодексом РК и Законом РК от 10.12.1999 № 493-I «О труде»") == []
    assert check_requisites("статьёй 172 Гражданского кодекса РК. Закон № 94-V") == []


def test_topics_map_clause_subject_to_article():
    assert expected_article(f"6.2. Отпуск может быть разделён на части по статье 90 {TK}.", "ТК РК").article == "92"
    assert expected_article("10.4. Договор составлен в двух экземплярах, ст. 45 ТК РК.", "ТК РК").article == "33"
    assert expected_article("Отпуск разделён на части", "ГК РК") is None


def test_art37_notice_rule():
    [v] = qualitative_check(f"трудовой договор\n3.2. Если Работник признан не выдержавшим испытание, договор "
                            f"расторгается в порядке, установленном {TK}.")
    assert (v.clause, v.correct_article) == ("3.2", "37")
    assert qualitative_check("трудовой\n3.2. Не выдержавшего испытание работника уведомляют письменно "
                             "с указанием причин.") == []


def test_405_ii_repeal_date():
    assert "1 января 2020" in REGISTRY["405-II"].status and "286-VI" in REGISTRY["405-II"].status


async def test_llm_correct_article_overridden_by_rule_and_unsupported_cleared():
    reply = json.dumps({"findings": [
        {"clause": "6.2", "lawCode": "ТК РК", "citedArticle": "90", "verdict": "WRONG_ARTICLE", "correctArticle": "94"},
        {"clause": "6.2", "lawCode": "ТК РК", "citedArticle": "90", "verdict": "WRONG_ARTICLE", "correctArticle": "92"},
        {"clause": "7.1", "lawCode": "ТК РК", "citedArticle": "22", "verdict": "WRONG_ARTICLE", "correctArticle": "777"},
    ], "okArticles": [], "summary": ""})
    laws = FakeLaws(articles={"ТК РК": {"90": {"articleNumber": "90", "articleTitle": "Исчисление"},
                                         "22": {"articleNumber": "22", "articleTitle": "Права и обязанности"}}},
                    titles={"ТК РК": [{"articleNumber": "90", "articleTitle": "Исчисление"}]})
    c = make_container(llm=FakeLLM(complete=lambda s, u: reply), laws=laws)
    a = await c.audit.audit(f"трудовой договор\n6.2. Отпуск может быть разделён на части согласно статье 90 {TK}.\n"
                            f"7.1. Работник соблюдает трудовую дисциплину согласно статье 22 {TK}.")
    wrong = [f for f in a.findings if f.verdict == "WRONG_ARTICLE"]
    split = [f for f in wrong if f.clause == "6.2"]
    assert [f.correct_article for f in split] == ["92"]
    [other] = [f for f in wrong if f.clause == "7.1"]
    assert other.correct_article == "" and "требует проверки" in other.explanation


async def test_requisite_issue_reaches_audit_and_report():
    llm = FakeLLM(complete=lambda s, u: "{}")
    c = make_container(llm=llm)
    a = await c.audit.audit("10.2. Споры — по Гражданскому процессуальному кодексу РК от 31.10.2015 № 377-VI.")
    [f] = [f for f in a.findings if f.verdict == "WRONG_REQUISITES"]
    assert f.clause == "10.2" and "377-V" in f.explanation
    assert "ОШИБКИ В РЕКВИЗИТАХ КОДЕКСОВ" in llm.prompts[0]


async def test_regex_ner_yo_article_form_and_sum_with_words():
    from app.ml.ner import RegexNer
    ents = await RegexNer().extract("3.1. В соответствии со статьёй 49 ТК РК. Оклад 450 000 (четыреста пятьдесят тысяч) тенге.")
    assert any(e.label == "article" and e.attrs["number"] == "49" for e in ents)
    assert any(e.label == "sum" and e.text.startswith("450 000 (") for e in ents)


def test_gliner_postfilter():
    from app.ml.ner import plausible
    assert not plausible("org", "Заказчик") and not plausible("org", "Стороны")
    assert plausible("org", "ТОО «Data Test»")
    assert not plausible("sum", "Общая стоимость услуг") and plausible("sum", "625 000 тенге")
    assert not plausible("term", "испытательный срок") and plausible("term", "в течение трёх рабочих дней")
    assert not plausible("term", "31.12.2027")
