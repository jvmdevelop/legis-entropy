from app.analysis.citation_verifier import CitationVerifier, _Item, _Items
from app.analysis.verifier import Verification
from app.schemas import AuditFinding
from tests.test_analysis_fakes import FakeLLM, FakeLaws

LAWS = FakeLaws(articles={"ГК РК": {
    "100": {"articleNumber": "100", "articleTitle": "Наследование по закону", "excerpt": "Наследники..."},
    "546": {"articleNumber": "546", "articleTitle": "Арендная плата", "excerpt": "Арендная плата вносится..."}}})


def _f(verdict="WRONG_ARTICLE", source="llm", cited="100", correct="546", law="ГК РК"):
    return AuditFinding(clause="4.2", law_code=law, cited_article=cited, verdict=verdict, correct_article=correct,
                        explanation="про аренду", source=source)


def _llm(*statuses):
    return FakeLLM(structured=lambda s, u, schema: _Items(items=[_Item(i=i, status=st, reason=f"r{i}")
                                                                 for i, st in enumerate(statuses)]))


async def test_confirmed_finding_keeps_verdict():
    llm = _llm("CONFIRMED")
    [f] = await CitationVerifier(LAWS, llm).verify([_f()], Verification([], [], []))
    assert (f.verdict, f.verification, f.verification_note) == ("WRONG_ARTICLE", "CONFIRMED", "r0")
    body = llm.structured_calls[0][1]
    assert "Наследование по закону" in body and "Арендная плата" in body


async def test_refuted_finding_becomes_unverified():
    [f] = await CitationVerifier(LAWS, _llm("REFUTED")).verify([_f()], Verification([], [], []))
    assert f.verdict == "UNVERIFIED" and f.correct_article == "" and "опровергнута" in f.explanation


async def test_no_texts_insufficient_without_llm_call():
    llm = _llm("CONFIRMED")
    [f] = await CitationVerifier(FakeLaws(), llm).verify([_f(law="ТК РК", cited="1", correct="2")],
                                                         Verification([], [], []))
    assert f.verification == "INSUFFICIENT" and llm.structured_calls == []


async def test_rule_findings_and_llm_failure():
    llm = FakeLLM(structured=lambda s, u, schema: (_ for _ in ()).throw(RuntimeError("down")))
    rule, ml = _f(source="rule"), _f(source="ml")
    await CitationVerifier(LAWS, llm).verify([rule, ml], Verification([], [], []))
    assert rule.verification is None
    assert ml.verification == "INSUFFICIENT" and ml.verdict == "WRONG_ARTICLE"


async def test_benchmark_offline_regression():
    from pathlib import Path
    from bench.run import run_case
    res = await run_case(Path(__file__).parents[1] / "bench/cases/work_contract_kz.json", live=False)
    assert res["reference_errors"]["recall"] >= 0.88 and res["reference_errors"]["fp"] == 0
    assert res["correct_article"]["fp"] == 0
    for k in ("repealed", "violations", "missing_clauses"):
        assert res[k]["recall"] == 1.0 and res[k]["fp"] == 0, k


async def test_purpose_check_marks_imprecise_only_on_mismatch():
    from app.analysis.references import ArticleReference
    refs = [ArticleReference("ТК РК", "33", "1.3. Согласно статье 33 ТК РК устанавливаются взаимоотношения.", 0),
            ArticleReference("ТК РК", "113", "5.2. Зарплата выплачивается по статье 113 ТК РК.", 50)]
    v = Verification(refs, [], [], cited={"ТК РК": {"33": {"articleTitle": "Порядок заключения"},
                                                    "113": {"articleTitle": "Сроки выплаты заработной платы"}}})
    ok33 = AuditFinding(clause="1.3", law_code="ТК РК", cited_article="33", verdict="OK", source="ml")
    ok113 = AuditFinding(clause="5.2", law_code="ТК РК", cited_article="113", verdict="OK", source="ml")
    rule = AuditFinding(clause="5.2", law_code="ТК РК", cited_article="113", verdict="OK", source="rule")
    llm = _llm("IMPRECISE", "EXACT")
    await CitationVerifier(LAWS, llm).check_purpose([ok33, ok113, rule], v)
    assert ok33.verdict == "IMPRECISE" and "неточная" in ok33.explanation
    assert ok113.verdict == "OK" and rule.verdict == "OK"
    first, second = llm.structured_calls
    assert "[1]" in first[1] and "[2]" not in first[1]
    assert "Пункт 1.3" in second[1] and "[1]" not in second[1]


async def test_purpose_flag_dropped_when_not_repeated():
    from app.analysis.references import ArticleReference
    refs = [ArticleReference("ТК РК", "113", "5.2. Зарплата по статье 113 ТК РК.", 0)]
    v = Verification(refs, [], [], cited={"ТК РК": {"113": {"articleTitle": "Сроки выплаты"}}})
    f = AuditFinding(clause="5.2", law_code="ТК РК", cited_article="113", verdict="OK", source="ml")
    answers = iter([["IMPRECISE"], ["EXACT"]])
    llm = FakeLLM(structured=lambda s, u, schema: _Items(items=[_Item(i=i, status=st) for i, st in
                                                                enumerate(next(answers))]))
    await CitationVerifier(LAWS, llm).check_purpose([f], v)
    assert f.verdict == "OK"


def test_repealed_llm_codes_normalized_or_dropped():
    import json
    from app.analysis.audit import parse
    raw = json.dumps({"findings": [
        {"clause": "8.1", "lawCode": "Закон РК № 405-II", "verdict": "REPEALED", "explanation": "утратил силу"},
        {"clause": "8.2", "lawCode": "Закон РК", "verdict": "REPEALED", "explanation": "заменён Законом № 105-V"},
        {"clause": "10.3", "lawCode": "ГК РК (Особенная часть)", "citedArticle": "172", "verdict": "WRONG_ARTICLE"}]})
    findings, _ = parse(raw)
    assert [(f.clause, f.law_code) for f in findings] == [("8.1", "405-II"), ("10.3", "ГК РК")]


def test_llm_proposed_article_kept_when_real_title_quoted():
    from app.analysis.audit import _title_quoted
    v = Verification([], [], [], toc={"ТК РК": {"21": "Основания возникновения трудовых отношений"}})
    ok = AuditFinding(law_code="ТК РК", cited_article="33", correct_article="21", verdict="WRONG_ARTICLE",
                      explanation="подходит ст. 21 «Основания возникновения трудовых отношений»")
    bad = ok.model_copy(update={"explanation": "подходит ст. 21 «Права работника»"})
    assert _title_quoted(ok, v) and not _title_quoted(bad, v)
