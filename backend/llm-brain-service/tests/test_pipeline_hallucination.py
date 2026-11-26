from app.ml.embeddings import TrigramEmbedder
from app.ml.ner import RegexNer
from app.pipeline.context import Citation, DocumentContext
from app.pipeline.hallucination import (UNVERIFIED_MARK, AnswerPostProcessor, ArticleIndex, ClaimVerifier,
                                        HallucinationDetector, split_sentences)
from app.pipeline.types import ContextPlan, RetrievalMode as R, TaskType as T
from tests.test_pipeline_fakes import FakeLaws, SubstringNli

PLAN = ContextPlan(T.LAW_SEARCH, R.LAW)


def ctx(*cits):
    return DocumentContext.of("ctx", list(cits))


async def test_ungrounded_article_redacted_with_note():
    post = AnswerPostProcessor(HallucinationDetector(ArticleIndex(FakeLaws())))
    c = ctx(Citation("1", article_number=88, law_code="414-V", text="Статья 88 о расторжении"))
    out = await post.process("Согласно статья 88 ТК РК работник вправе... А статья 999 ТК РК запрещает это всё.",
                             PLAN, c)
    assert "статья 88 ТК РК" in out
    assert "**статья [номер требует проверки — 999]**" in out and "**999**" in out


async def test_existing_article_in_law_index_not_redacted():
    post = AnswerPostProcessor(HallucinationDetector(ArticleIndex(FakeLaws(titles={"414-V": ["52"]}))))
    out = await post.process("Работодатель обязан соблюдать статья 52 ТК РК во всех случаях найма.", PLAN, ctx())
    assert "требует проверки" not in out


async def test_short_answer_note_and_empty():
    post = AnswerPostProcessor(HallucinationDetector())
    assert (await post.process("  ", PLAN, ctx())) == "Не удалось получить ответ от LLM."
    out = await post.process("Да.", PLAN, ctx(Citation("1")))
    assert out.endswith("Проверьте вывод вручную.")


def test_sentence_split_keeps_article_abbrev():
    text = "Согласно ст. 5 ТК РК срок составляет три года. Следующее предложение достаточно длинное."
    spans = [text[s:e] for s, e in split_sentences(text)]
    assert spans[0] == "Согласно ст. 5 ТК РК срок составляет три года."


async def test_nli_marks_unsupported_claims():
    v = ClaimVerifier(SubstringNli("три года"), RegexNer(), TrigramEmbedder(), 0.8)
    c = ctx(Citation("1", text="Общий срок исковой давности составляет три года (ст. 178 ГК РК)."))
    answer = ("Срок исковой давности по ст. 178 ГК РК составляет три года.\n"
              "Неустойка по ст. 293 ГК РК не может превышать 10 000 тенге в любом случае.")
    out, checks = await v.mark(answer, c)
    assert [x.supported for x in checks] == [True, False]
    assert out.count(UNVERIFIED_MARK) == 1 and out.endswith("в любом случае." + UNVERIFIED_MARK)


async def test_nli_skipped_without_sources():
    v = ClaimVerifier(SubstringNli("x"), RegexNer(), None, 0.8)
    out, checks = await v.mark("Срок по ст. 5 ТК РК составляет три года.", DocumentContext.missing("нет"))
    assert checks == [] and UNVERIFIED_MARK not in out
