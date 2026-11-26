import json
import logging
import re
from dataclasses import dataclass

from app.analysis.citation_verifier import CitationVerifier
from app.analysis.labor import Violation
from app.analysis.references import canonical_code
from app.analysis.repealed import ACT_NUMBER
from app.analysis.topics import expected_article
from app.analysis.verifier import ArticleReferenceVerifier, Verification, clause_number, meta
from app.schemas import AuditFinding, AuditResponse
from app.llm.client import Completer

log = logging.getLogger(__name__)

VERDICTS = ("OK", "WRONG_ARTICLE", "NOT_FOUND", "REPEALED", "VIOLATION", "UNVERIFIED", "WRONG_REQUISITES", "IMPRECISE")
_OK_ARTICLE = re.compile(r"\s*(.+?)\s+(?:ст\.?\s*)?(\d+(?:-\d+)?)\s*")
HEADER = "### Проверка ссылок документа\n\n"
SYSTEM = "Ты — помощник юриста, который сверяет ссылки на нормы права. Отвечай строго JSON."
PROMPT = """Сверка ссылок документа с официальными текстами законов РК:

{report}

Найди ПРОБЛЕМНЫЕ ссылки. Правильный номер бери только из строк «по смыслу пункта подходит» или из оглавления кодекса.
Верни ТОЛЬКО JSON:
{{"findings":[{{"clause":"3.1","lawCode":"ТК РК","citedArticle":"49","verdict":"WRONG_ARTICLE","correctArticle":"36","explanation":"коротко"}}],
 "okArticles":["ТК РК 30","ТК РК 33"],
 "summary":"2 предложения: сколько ссылок проверено, сколько проблем, что главное"}}
verdict: WRONG_ARTICLE — статья про другое; NOT_FOUND — статьи нет; REPEALED — акт утратил силу
(lawCode = номер акта, citedArticle пусто); VIOLATION — условие пункта противоречит тексту статьи
(например, срок меньше установленного законом) — отдельной записью. Корректные ссылки не включай
в findings, перечисли их в okArticles как «код номер».
"""
CHECK_MARKS = {"CONFIRMED": "🔎 Подтверждено независимой проверкой по тексту статьи",
               "INSUFFICIENT": "🔎 Независимая проверка: недостаточно данных"}
ICONS = {"IMPRECISE": "〰️", "WRONG_REQUISITES": "🔢", "VIOLATION": "⛔", "REPEALED": "🗑", "NOT_FOUND": "❓", "WRONG_ARTICLE": "⚠️", "OK": "✅", "UNVERIFIED": "❔"}
LABELS = {"IMPRECISE": "Неточная ссылка", "WRONG_REQUISITES": "Ошибка в реквизитах акта", "VIOLATION": "Нарушение закона", "REPEALED": "Акт утратил силу", "NOT_FOUND": "Статья не существует",
          "WRONG_ARTICLE": "Ошибочная ссылка", "OK": "Корректно", "UNVERIFIED": "Не подтверждено"}


@dataclass
class Audit:
    findings: list[AuditFinding]
    summary: str
    markdown: str

    @property
    def problems(self) -> int:
        return sum(f.is_problem for f in self.findings)

    def response(self) -> AuditResponse:
        return AuditResponse(summary=self.summary, markdown=self.markdown, problems=self.problems,
                             findings=self.findings)


def _finding(clause="", law_code="", cited="", verdict="OK", correct="", explanation="", confidence=None,
             source="llm"):
    return AuditFinding(clause=clause, law_code=law_code, cited_article=cited, verdict=verdict,
                        correct_article=correct, explanation=explanation, confidence=confidence, source=source)


def _digits(v) -> str:
    return re.sub(r"[^0-9-]", "", "" if v is None else str(v))


def _s(v) -> str:
    return "" if v is None else str(v)


def _law(code: str, verdict: str) -> str:
    if verdict == "REPEALED" and (m := ACT_NUMBER.search(code)):
        return re.sub(r"\s", "", m.group(1)).replace("І", "I").upper()
    return canonical_code(code) or code.strip()


def parse(raw: str) -> tuple[list[AuditFinding], str] | None:
    try:
        root = json.loads(raw[raw.index("{"): raw.rindex("}") + 1])
    except (ValueError, TypeError) as e:
        log.warning("Could not parse reference audit JSON: %s", e)
        return None
    findings = []
    for f in root.get("findings") or []:
        verdict = _s(f.get("verdict") or "OK").strip().upper()
        if verdict not in VERDICTS[:5]:
            continue
        law = _law(" ".join(_s(f.get(k)) for k in ("lawCode", "citedArticle")) if verdict == "REPEALED"
                   else _s(f.get("lawCode")), verdict)
        if verdict == "REPEALED" and not ACT_NUMBER.fullmatch("№ " + law):
            continue
        findings.append(_finding(_s(f.get("clause")), law, _digits(f.get("citedArticle")) if verdict != "REPEALED" else "", verdict,
                                 _digits(f.get("correctArticle")), _s(f.get("explanation"))))
    for ok in root.get("okArticles") or []:
        m = _OK_ARTICLE.fullmatch(_s(ok))
        if m:
            findings.append(_finding(law_code=m.group(1).strip(), cited=m.group(2)))
    return findings, _s(root.get("summary"))


def with_rules(findings: list[AuditFinding], rules: list[Violation]) -> list[AuditFinding]:
    ok_keys = {(f.law_code, f.cited_article) for f in findings if f.verdict == "OK"}
    kept = []
    for f in findings:
        key = (f.law_code, f.cited_article)
        if f.verdict == "WRONG_ARTICLE" and f.correct_article == f.cited_article:
            ok_keys.add(key)
            kept.append(_finding(f.clause, f.law_code, f.cited_article, "OK"))
            continue
        if f.verdict == "WRONG_ARTICLE" and key in ok_keys:
            continue
        if (f.verdict == "VIOLATION" and f.correct_article == f.cited_article
                and not any(r.correct_article == f.correct_article for r in rules)):
            continue
        if f.verdict == "OK" and any(r.cited_article == f.cited_article for r in rules):
            continue
        kept.append(f)
    for r in rules:
        kept = [f for f in kept if not (f.verdict == "VIOLATION" and f.correct_article == r.correct_article)]
        kept.append(_finding(r.clause, r.law_code, r.cited_article, "VIOLATION", r.correct_article, r.explanation,
                             source="rule"))
    return kept


def _per_clause(findings: list[AuditFinding], v: Verification) -> list[AuditFinding]:
    clauses: dict[tuple[str, str], list[str]] = {}
    for r in v.refs:
        lst = clauses.setdefault((r.law_code, r.article_number), [])
        if (c := clause_number(r.clause)) and c not in lst:
            lst.append(c)
    out = []
    for f in findings:
        targets = clauses.get((f.law_code, f.cited_article)) if not f.clause and f.cited_article else None
        if not targets:
            out.append(f)
            continue
        out += [f.model_copy(update={"clause": c}) for c in targets]
    return out


def supplement(findings: list[AuditFinding], v: Verification) -> list[AuditFinding]:
    findings = _per_clause(findings, v)
    ml = {(f.clause, f.law_code, f.cited_article): f for f in v.ml_findings}
    ml_any = {(f.law_code, f.cited_article): f for f in v.ml_findings}
    covered = {(f.clause, f.law_code, f.cited_article) for f in findings if f.clause and f.verdict != "VIOLATION"}
    covered_any = {(f.law_code, f.cited_article) for f in findings if not f.clause}
    for f in findings:
        m = ml.get((f.clause, f.law_code, f.cited_article)) or ml_any.get((f.law_code, f.cited_article))
        if f.confidence is None and m is not None:
            f.confidence = m.confidence
    rules = {(r.law_code, r.correct_article) for r in v.violations}
    rule_cited = {r.cited_article for r in v.violations}
    out = list(findings)
    for key, f in ml.items():
        if key in covered or key[1:] in covered_any or (f.verdict == "OK" and f.cited_article in rule_cited):
            continue
        if f.verdict == "VIOLATION" and (f.law_code, f.cited_article) in rules:
            continue
        f.source = f.source or "ml"
        out.append(f)
        covered.add(key)
    repealed_done = {f.law_code for f in out if f.verdict == "REPEALED"}
    for m in v.repealed:
        if m.act.number not in repealed_done:
            out.append(_finding(clause_number(m.clause), m.act.number, "", "REPEALED", "",
                                f"{m.act.title} — {m.act.status}.", source="registry"))
    for r in v.requisites:
        out.append(_finding(clause_number(r.clause), r.code, r.cited_number, "WRONG_REQUISITES", r.correct,
                            r.explanation, 1.0,
                            source="rule"))
    return out


def _norm_title(t: str) -> str:
    return re.sub(r"[^\w]+", " ", (t or "").lower().replace("ё", "е")).strip()


def _title_quoted(f: AuditFinding, v: Verification) -> bool:
    title = _norm_title(v.toc.get(f.law_code or "", {}).get(f.correct_article or "", ""))
    return len(title) >= 10 and title in _norm_title(f.explanation or "")


def validate(findings: list[AuditFinding], v: Verification) -> list[AuditFinding]:
    clauses = {clause_number(r.clause): r.clause for r in v.refs}
    ml_alt = {(f.law_code, f.cited_article): f.correct_article for f in v.ml_findings if f.verdict == "WRONG_ARTICLE"}
    out, seen = [], set()
    for f in findings:
        if f.verdict in ("WRONG_ARTICLE", "OK", "UNVERIFIED") and f.cited_article:
            text = clauses.get(f.clause or "")
            topic = expected_article(text, f.law_code)
            if topic and f.cited_article in topic.allowed:
                f = _finding(f.clause, f.law_code, f.cited_article, "OK", confidence=f.confidence, source="rule")
            elif topic and f.verdict == "WRONG_ARTICLE" and f.correct_article in topic.allowed:
                pass
            elif topic:
                f = _finding(f.clause, f.law_code, f.cited_article, "WRONG_ARTICLE", topic.article,
                             f"Предмет пункта — {topic.title}: это ст. {topic.article} {f.law_code}, "
                             f"а не ст. {f.cited_article}.", f.confidence, source="rule")
            elif f.verdict == "WRONG_ARTICLE" and f.correct_article:
                supported = {meta(h, "articleNumber") for h in v.by_meaning.get(text or "", [])}
                supported.add(ml_alt.get((f.law_code, f.cited_article)))
                if f.correct_article not in supported and not _title_quoted(f, v):
                    f.correct_article = ""
                    f.explanation = (f.explanation or "").rstrip(". ") + ". Правильная статья требует проверки."
        key = (f.clause, f.law_code, f.cited_article, f.verdict == "VIOLATION")
        if f.cited_article and key in seen:
            continue
        seen.add(key)
        out.append(f)
    return out


def summarize(findings: list[AuditFinding]) -> str:
    n = lambda *vs: sum(f.verdict in vs for f in findings)  # noqa: E731
    ok, wrong, repealed, violations = n("OK"), n("WRONG_ARTICLE", "NOT_FOUND", "WRONG_REQUISITES"), n("REPEALED"), n("VIOLATION")
    s = (f"Проверено ссылок: {ok + wrong + repealed}. Корректных — {ok}, ошибочных — {wrong}, "
         f"на утратившие силу акты — {repealed}. Нарушений закона по существу — {violations}.")
    imprecise = n("IMPRECISE")
    s += f" Неточных ссылок (статья смежная, но не прямое основание) — {imprecise}." if imprecise else ""
    unverified = n("UNVERIFIED")
    s += f" Не подтверждено автоматически — {unverified}." if unverified else ""
    checked = [f for f in findings if f.verification]
    if checked:
        c = lambda st: sum(f.verification == st for f in checked)  # noqa: E731
        s += (f" Независимая проверка находок: подтверждено {c('CONFIRMED')}, опровергнуто {c('REFUTED')}, "
              f"недостаточно данных {c('INSUFFICIENT')}.")
    return s


def render(findings: list[AuditFinding], summary: str) -> str:
    r = [HEADER]
    if summary.strip():
        r.append(summary + "\n\n")
    for f in (x for x in findings if x.is_problem):
        line = f"- {ICONS[f.verdict]} **{LABELS[f.verdict]}**"
        if f.clause:
            line += f" · п. {f.clause}"
        if f.verdict == "WRONG_REQUISITES":
            line += f" · {f.law_code} № {f.cited_article}" + (f" → правильно: № {f.correct_article}"
                                                              if f.correct_article else "")
        else:
            if f.cited_article:
                line += f" · ст. {f.cited_article} {f.law_code}"
            elif f.law_code:
                line += f" · {f.law_code}"
            if f.correct_article:
                line += f" → правильно: ст. {f.correct_article}"
        if f.explanation:
            line += f"  \n  {f.explanation}"
        if f.verification in CHECK_MARKS and f.verdict != "UNVERIFIED":
            line += f"  \n  {CHECK_MARKS[f.verification]}" + (f": {f.verification_note}" if f.verification_note else "")
        elif f.source in ("rule", "registry"):
            line += "  \n  _Источник: детерминированное правило / реестр платформы._"
        r.append(line + "\n")
    ok = list(dict.fromkeys(f"ст. {f.cited_article} {f.law_code}" for f in findings if not f.is_problem))
    if ok:
        r.append("\nКорректные ссылки: " + ", ".join(ok) + "\n")
    return "".join(r)


class ReferenceAuditService:
    def __init__(self, verifier: ArticleReferenceVerifier, llm: Completer,
                 citations: CitationVerifier | None = None):
        self.verifier, self.llm, self.citations = verifier, llm, citations

    async def audit(self, text: str) -> Audit:
        v = await self.verifier.verify(text)
        if v.empty or not v.report.strip():
            return Audit([], "", "")
        parsed = None
        try:
            raw = await self.llm.complete(SYSTEM, PROMPT.format(report=v.report))
            parsed = parse(raw)
        except Exception as e:
            log.warning("Reference audit LLM call failed: %s", e)
        llm_findings = parsed[0] if parsed else []
        findings = validate(supplement(with_rules(llm_findings, v.violations), v), v)
        if self.citations is not None:
            findings = await self.citations.verify(findings, v)
            findings = await self.citations.check_purpose(findings, v)
        if not findings:
            return Audit([], "", HEADER + v.report)
        summary = summarize(findings)
        return Audit(findings, summary, render(findings, summary))
