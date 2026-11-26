from pathlib import Path

from app.analysis.civil import civil_check
from app.analysis.topics import expected_article
from app.pipeline.quotes import NOT_FOUND, QuoteChecker

DOC = ("3.1. Стороны устанавливают испытательный срок продолжительностью два (2) месяца.\n"
       "6.1. Работнику предоставляется оплачиваемый ежегодный трудовой отпуск продолжительностью "
       "не менее двадцати одного календарного дня.\n")


def test_exact_quote_untouched():
    ans = "В п. 3.1: «Стороны устанавливают испытательный срок продолжительностью два (2) месяца»."
    out, r = QuoteChecker(DOC).check(ans)
    assert out == ans and r.exact == 1


def test_near_quote_replaced_with_document_text():
    ans = "Сказано «работнику предоставляется оплачиваемый ежегодный трудовой отпуск продолжительностью не менее двадцати календарных дней»."
    out, r = QuoteChecker(DOC).check(ans)
    assert "двадцати одного календарного дня»" in out and r.fixed == 1


def test_invented_quote_flagged_and_short_or_titles_ignored():
    ans = ("Есть условие «работник обязан работать по субботам без дополнительной оплаты труда». "
           "Закон «О персональных данных и их защите». Термин «Работодатель».")
    out, r = QuoteChecker(DOC).check(ans)
    assert out.count(NOT_FOUND) == 1 and r.not_found == 1


def test_wrong_clause_number_fixed():
    out, r = QuoteChecker(DOC).check("Согласно п. 4.1: «Стороны устанавливают испытательный срок продолжительностью два (2) месяца».")
    assert "п. 3.1:" in out and r.clause_fixed == 1


def test_civil_rules():
    text = ("5.2. Неустойка не подлежит уменьшению ни при каких обстоятельствах.\n"
            "8.1. Срок исковой давности по требованиям из договора составляет один год.\n"
            "8.2. Срок исковой давности — три года.\n"
            "9.1. Исполнитель вправе отказаться от договора без возмещения убытков.\n"
            "9.2. Заказчик не вправе отказаться от исполнения договора.\n")
    found = {(v.clause, v.correct_article, v.law_code) for v in civil_check(text)}
    assert found == {("5.2", "297", "ГК РК"), ("8.1", "177", "ГК РК"), ("9.1", "686", "ГК РК"), ("9.2", "686", "ГК РК")}


def test_gk_topics_allow_related_articles():
    t = expected_article("5.1. Неустойка уменьшается судом по статье 297 ГК РК.", "ГК РК")
    assert t.article == "293" and "297" in t.allowed and "145" not in t.allowed
    assert expected_article("3.1. Арендная плата вносится ежемесячно.", "ГК РК").article == "546"
    assert expected_article("3.1. Арендная плата вносится ежемесячно.", "НК РК") is None


async def test_lease_benchmark_regression():
    from bench.run import run_case
    res = await run_case(Path(__file__).parents[1] / "bench/cases/lease_contract_kz.json", live=False)
    for k in ("reference_errors", "correct_article", "violations", "missing_clauses", "requisites"):
        assert res[k]["recall"] == 1.0 and res[k]["fp"] == 0, k


async def test_rewrite_fragment():
    from app.edits.service import DocumentEditService, _Rewrite
    from tests.test_analysis_fakes import FakeLLM
    llm = FakeLLM(structured=lambda s, u, schema: _Rewrite(replacement="три (3) месяца", reason="ст. 36 ТК РК"))
    svc = DocumentEditService(None, None, llm)
    p = await svc.rewrite(DOC, "два (2) месяца", "сделай максимальный срок")
    assert (p.clause, p.original, p.replacement, p.source) == ("3.1", "два (2) месяца", "три (3) месяца", "llm")
    assert "Инструкция пользователя: сделай максимальный срок" in llm.structured_calls[0][1]
    assert await svc.rewrite(DOC, "такого фрагмента нет") is None
