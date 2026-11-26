import inspect
import logging
import re
from dataclasses import dataclass
from typing import Any, Callable

from app.clients.ports import GraphPort, SituationPort
from app.config import DeepAnalysisSettings
from app.ports import ReferenceAuditor
from app.graph.laws import article_text, default_country, rank, snippet
from app.graph.link.base import LinkableSubject, SubjectLinker
from app.graph.primary import PrimaryLawContext, PrimaryLawResolver
from app.ml import text as T
from app.ml.embeddings import Embedder
from app.ml.hybrid import HybridRanker
from app.ml.nli import NliModel, NliResult
from app.schemas import ProgressEvent

log = logging.getLogger(__name__)

IDEAL_DEPTH = ["идеальн", "максимальн", "исчерпыва", "очень глубок", "максимально глубок", "самый глубок",
               "большой анализ"]
CONTRADICTION_THRESHOLD = 0.7
MAX_SENTENCES = 80

Progress = Callable[[ProgressEvent], Any]


def classify_depth(message: str | None) -> int:
    m = T.normalize(message)
    return 3 if any(k in m for k in IDEAL_DEPTH) else 2


def is_contradiction(r: NliResult, threshold: float = CONTRADICTION_THRESHOLD) -> bool:
    return r.label == "contradiction" and r.contradiction >= threshold


@dataclass
class DeepAnalysisResult:
    primary_laws: int
    secondary_laws: int
    tertiary_laws: int
    articles_linked: int
    conflicts_found: int
    report: str

    @staticmethod
    def empty(message: str) -> "DeepAnalysisResult":
        return DeepAnalysisResult(0, 0, 0, 0, 0, message)


@dataclass
class RankedArticle:
    article: dict
    score: float
    doc_snippet: str = ""


class _DocIndex:
    def __init__(self, embedder: Embedder, text: str):
        self.embedder = embedder
        self.text = text
        self.sentences = [s for s in re.split(r"(?<=[.!?])\s+|\n+", text) if len(s.strip()) > 15][:MAX_SENTENCES]
        self._vecs: list[list[float]] | None = None

    async def best(self, articles: list[dict]) -> list[str]:
        if not self.sentences or not articles:
            return [snippet(self.text, 220)] * len(articles)
        try:
            if self._vecs is None:
                self._vecs = await self.embedder.embed([s[:500] for s in self.sentences])
            avecs = await self.embedder.embed([article_text(a)[:1000] for a in articles])
        except Exception as e:
            log.debug("snippet embedding failed: %s", e)
            return [snippet(self.text, 220)] * len(articles)
        out = []
        for av in avecs:
            i = max(range(len(self._vecs)), key=lambda j: T.cosine(av, self._vecs[j]))
            out.append(snippet(self.sentences[i], 220))
        return out


class DeepAnalysisService:
    def __init__(self, graph: GraphPort, situations: SituationPort, resolver: PrimaryLawResolver,
                 ranker: HybridRanker, embedder: Embedder, nli: NliModel, settings: DeepAnalysisSettings,
                 auditor: ReferenceAuditor | None = None):
        self.graph, self.situations, self.resolver = graph, situations, resolver
        self.ranker, self.embedder, self.nli, self.s = ranker, embedder, nli, settings
        self.auditor = auditor

    async def run(self, graph_id: str, linker: SubjectLinker, subject: LinkableSubject, country: str | None,
                  depth: int, progress: Progress | None = None) -> DeepAnalysisResult:
        ctry = default_country(country)
        depth = max(1, min(depth, 3))
        emit = _emitter(progress)
        gen = linker.display_genitive
        emit("start", 0, 0, f"Запускаю глубокий анализ {gen} (depth={depth})…")

        try:
            doc = await linker.fetch_text(subject, graph_id) or ""
        except Exception as e:
            log.warning("DeepAnalysis: fetch_text failed for %s %s: %s", linker.kind, subject.id, e)
            doc = ""
        if not doc.strip():
            return DeepAnalysisResult.empty(f"Не удалось получить текст {gen} — попробуйте ещё раз через несколько секунд.")

        audit_md, audit_problems = await self._audit(graph_id, linker, subject, doc, ctry, emit)
        primary = await self._primary(graph_id, linker, subject, doc, ctry, emit)
        if not primary:
            return DeepAnalysisResult.empty(audit_md or f"В базе не найдено законов, релевантных содержимому {gen}.")

        visited = {l["code"] for l in primary}
        by_law: dict[str, list[RankedArticle]] = {}
        index = _DocIndex(self.embedder, doc)
        articles_linked, secondary, tertiary = 0, [], []
        if depth >= 2:
            articles_linked = await self._articles(graph_id, linker, subject, doc, index, ctry, primary, by_law, emit)
            secondary = await self._hop(graph_id, ctry, primary, visited, self.s.related_hop2, "related_laws_2",
                                        "Расширяю графа связанными нормами…", "Добавлен связанный закон ", emit)
        if depth >= 3 and secondary:
            tertiary = await self._hop(graph_id, ctry, secondary, visited, self.s.related_hop3, "related_laws_3",
                                       "Расширяю граф до 3-го уровня…", "Добавлен закон 3-го уровня ", emit)
        conflicts = 0
        if depth >= 3:
            emit("conflicts", 0, self.s.max_conflicts, "Проверяю противоречия в нормах…")
            conflicts = await self._conflicts(graph_id, linker, subject, ctry, primary, by_law, emit)
        conflicts += audit_problems

        report = build_report(primary, secondary, tertiary, by_law, articles_linked, conflicts, depth, gen)
        if audit_md:
            i = report.find("\n### ")
            report = report + "\n" + audit_md if i < 0 else report[:i + 1] + audit_md + "\n" + report[i + 1:]

        emit("persist", 0, 1, "Сохраняю комментарий в граф…")
        await self._persist(graph_id, linker, subject, report, primary + secondary + tertiary, by_law, ctry)
        emit("done", 1, 1, "Готово.")
        return DeepAnalysisResult(len(primary), len(secondary), len(tertiary), articles_linked, conflicts, report)

    async def _audit(self, graph_id, linker, subject, doc, ctry, emit) -> tuple[str, int]:
        emit("references", 0, 1, "Проверяю ссылки на статьи законов…")
        svc = self.auditor
        if svc is None or not hasattr(svc, "audit"):
            return "", 0
        try:
            audit = svc.audit(doc)
            audit = await audit if inspect.isawaitable(audit) else audit
        except Exception as e:
            log.warning("DeepAnalysis: reference audit failed: %s", e)
            return "", 0
        findings = list(getattr(audit, "findings", None) or [])
        attached: set[str] = set()
        for done, f in enumerate(findings, 1):
            code, cited = getattr(f, "law_code", None), getattr(f, "cited_article", None) or ""
            verdict = _verdict(f)
            problem = verdict != "OK"
            correct, expl = getattr(f, "correct_article", None) or "", getattr(f, "explanation", None) or ""
            clause = getattr(f, "clause", None) or ""
            if code and code.strip() and verdict != "REPEALED":
                try:
                    if code not in attached:
                        attached.add(code)
                        await self.graph.add_law(graph_id, code, ctry)
                        await linker.link_to_law(graph_id, subject, code, ctry)
                    ref = clause or "auto"
                    if problem and cited:
                        reason = {"WRONG_ARTICLE": f"Ошибочная ссылка на ст. {cited} {code}"
                                                   + (f" — правильно ст. {correct}" if correct else ""),
                                  "NOT_FOUND": f"Статьи {cited} нет в {code}",
                                  "VIOLATION": f"Нарушение: {expl}"}.get(verdict, expl)
                        await linker.flag_conflict(graph_id, subject, code, ctry, cited, ref, reason, 0.9)
                    good = correct if problem else cited
                    if good:
                        await linker.link_clause_to_article(graph_id, subject, code, ctry, good, ref, clause, expl, 0.9)
                except Exception as e:
                    log.warning("DeepAnalysis: could not mirror reference finding %s %s: %s", code, cited, e)
            emit("references", done, len(findings),
                 f"{'Проблема: ст. ' if problem else 'Ссылка верна: ст. '}{cited} {code}")
        problems = sum(1 for f in findings if _verdict(f) != "OK")
        return (getattr(audit, "markdown", "") or "").strip(), problems

    async def _primary(self, graph_id, linker, subject, doc, ctry, emit) -> list[dict]:
        n = self.s.primary_laws
        emit("primary_laws", 0, n, "Ищу прямые применимые нормы…")
        hits = await self.resolver.resolve(PrimaryLawContext(doc, ctry, linker, subject, graph_id, n))
        attached: list[dict] = []
        for law in list(hits.values())[:n]:
            if len(attached) >= self.s.max_laws_total:
                break
            try:
                await self.graph.add_law(graph_id, law["code"], ctry)
                if await linker.link_to_law(graph_id, subject, law["code"], ctry):
                    attached.append(law)
                    emit("primary_laws", len(attached), n, f"Добавлен закон {law['code']}")
            except Exception as e:
                log.warning("Failed to add or link primary law %s: %s", law["code"], e)
        return attached

    async def _rank_articles(self, doc: str, articles: list[dict]) -> list[RankedArticle]:
        items = [a for a in articles if article_text(a)]
        if not items:
            return []
        ranked = await rank(self.ranker, doc[:2000], items, article_text, self.s.articles_per_law)
        return [RankedArticle(a, round(min(0.95, s), 4)) for a, s in ranked]

    async def _articles(self, graph_id, linker, subject, doc, index, ctry, laws, by_law, emit) -> int:
        target = len(laws) * self.s.articles_per_law
        emit("articles", 0, target, "Подбираю наиболее релевантные статьи…")
        linked = 0
        for law in laws:
            if linked >= self.s.max_articles_total:
                break
            try:
                ranked = await self._rank_articles(doc, await self.graph.articles(law["code"], ctry))
            except Exception as e:
                log.warning("Failed to load articles for %s: %s", law["code"], e)
                continue
            if not ranked:
                continue
            by_law[law["code"]] = ranked
            for ra, snip in zip(ranked, await index.best([r.article for r in ranked])):
                ra.doc_snippet = snip
                if linked >= self.s.max_articles_total:
                    break
                num = str(ra.article.get("number"))
                if await linker.link_clause_to_article(graph_id, subject, law["code"], ctry, num, "auto", snip,
                                                       snippet(ra.article.get("body"), 220), ra.score):
                    linked += 1
                    emit("articles", linked, target, f"Связана ст. {num} {law['code']}")
        return linked

    async def _hop(self, graph_id, ctry, sources, visited, per_law, step, msg, ok_prefix, emit) -> list[dict]:
        cap = self.s.max_laws_total
        target = min(cap - len(visited), len(sources) * per_law)
        emit(step, 0, target, msg)
        added: list[dict] = []
        for parent in sources:
            if len(visited) >= cap:
                break
            try:
                related = await self.graph.related(parent["code"], ctry)
            except Exception as e:
                log.warning("Failed to load related laws for %s: %s", parent["code"], e)
                continue
            n = 0
            for rel in related:
                code = rel.get("code")
                if not code or code in visited:
                    continue
                if n >= per_law or len(visited) >= cap:
                    break
                try:
                    await self.graph.add_law(graph_id, code, ctry)
                except Exception as e:
                    log.warning("Failed to expand graph for related law %s: %s", code, e)
                    continue
                visited.add(code)
                added.append(rel)
                n += 1
                emit(step, len(added), target, ok_prefix + code)
        return added

    async def _conflicts(self, graph_id, linker, subject, ctry, primary, by_law, emit) -> int:
        limit = self.s.max_conflicts
        flat = [(law["code"], ra) for law in primary for ra in by_law.get(law["code"], []) if ra.article.get("body")]
        cands: list[tuple[str, tuple, tuple[str, str]]] = []
        for code, ra in flat:
            if ra.doc_snippet:
                cands.append(("doc", (code, ra), (ra.article["body"][:1500], ra.doc_snippet)))
        for i, (ca, a) in enumerate(flat):
            for cb, b in flat[i + 1:]:
                if ca != cb and len(cands) < len(flat) + limit * 3:
                    cands.append(("art", (ca, a, cb, b), (a.article["body"][:1500], b.article["body"][:600])))
        if not cands:
            return 0
        try:
            results = await self.nli.predict([c[2] for c in cands])
        except Exception as e:
            log.warning("NLI conflict scan failed: %s", e)
            return 0
        flagged = 0
        for (kind, data, _), r in zip(cands, results):
            if flagged >= limit:
                break
            if not is_contradiction(r):
                continue
            conf = round(r.contradiction, 3)
            if kind == "doc":
                code, ra = data
                num = str(ra.article.get("number"))
                reason = (f"{linker.display_name} противоречит {code} ст. {num} "
                          f"(NLI contradiction={conf:.2f}): «{snippet(ra.doc_snippet, 120)}»")
                ok = await linker.flag_conflict(graph_id, subject, code, ctry, num, "auto", reason, conf)
                label = f"ст. {num} {code}"
            else:
                ca, a, cb, b = data
                na, nb = str(a.article.get("number")), str(b.article.get("number"))
                try:
                    await self.situations.flag_article_conflict({
                        "graphId": graph_id, "country": ctry, "codeA": ca, "numberA": na, "codeB": cb, "numberB": nb,
                        "reason": f"Коллизия норм (NLI contradiction={conf:.2f})", "confidence": conf})
                    ok = True
                except Exception as e:
                    log.warning("Failed to flag article conflict: %s", e)
                    ok = False
                label = f"{ca} ст. {na} ↔ {cb} ст. {nb}"
            if ok:
                flagged += 1
                emit("conflicts", flagged, limit, f"Возможное противоречие: {label}")
        return flagged

    async def _persist(self, graph_id, linker, subject, report, laws, by_law, ctry) -> None:
        try:
            refs = [{"lawCode": code, "number": str(ra.article["number"]), "country": ctry,
                     "reason": f"Подобрана как релевантная статья (score={ra.score:.2f})"}
                    for code, ranked in by_law.items() for ra in ranked if ra.article.get("number") is not None]
            title = f"Анализ {linker.display_genitive}" + (f" · {subject.label}" if subject.label and subject.label.strip() else "")
            await self.graph.create_comment(graph_id, {
                "title": title, "body": report, "kind": "DEEP_ANALYSIS", "subjectKind": linker.kind.neo4j_label,
                "subjectId": subject.id, "referencedLawCodes": list(dict.fromkeys(l["code"] for l in laws if l.get("code"))),
                "referencedArticles": refs})
        except Exception as e:
            log.warning("Failed to persist analysis comment for graph %s: %s", graph_id, e)


def _verdict(f) -> str:
    v = getattr(f, "verdict", "OK")
    return str(getattr(v, "value", v))


def _emitter(progress: Progress | None):
    def emit(phase: str, done: int, total: int, message: str) -> None:
        if progress is None:
            return
        try:
            progress(ProgressEvent(phase=phase, done=done, total=total, message=message))
        except Exception:
            pass
    return emit


def _laws_section(title: str, laws: list[dict]) -> list[str]:
    if not laws:
        return []
    return [f"\n### {title} ({len(laws)})"] + [
        f"- **{l['code']}**" + (f": {l['title']}" if (l.get("title") or "").strip() else "") for l in laws]


def build_report(primary, secondary, tertiary, by_law, articles_linked, conflicts, depth, subject_label) -> str:
    r = [f"## Глубокий анализ {subject_label or 'субъекта'}",
         "_Уровень: " + ("идеальный (3 хопа + проверка конфликтов)" if depth == 3 else "глубокий (2 хопа)") + "_",
         "", f"### Прямые применимые нормы ({len(primary)})"]
    for law in primary:
        r.append(f"- **{law['code']}**" + (f": {law['title']}" if (law.get("title") or "").strip() else ""))
        for ra in by_law.get(law["code"], []):
            t = (ra.article.get("title") or "").strip()
            r.append(f"  - Ст. {ra.article.get('number')}" + (f" — {t}" if t else "") + f" _(score {ra.score:.2f})_")
    r += _laws_section("Связанные нормы 2-го уровня", secondary)
    r += _laws_section("Связанные нормы 3-го уровня", tertiary)
    if conflicts > 0:
        r += [f"\n### ⚠ Обнаружено возможных противоречий: {conflicts}",
              "_Помечены в графе как CONFLICTS_WITH; требуют проверки юристом._"]
    r += ["\n### Сводка", f"- Прямых норм: {len(primary)}", f"- Статей привязано к документу: {articles_linked}",
          f"- 2-й уровень: {len(secondary)}"]
    if depth >= 3:
        r.append(f"- 3-й уровень: {len(tertiary)}")
    r.append(f"- Конфликтов: {conflicts}")
    r.append("\n_Источник: PrimaryLawResolver (keyword-search → subject-seed → llm-fallback) + RELATES_TO BFS из "
             "graph-service; статьи — BGE-M3 + реранкер, противоречия — NLI (mDeBERTa-xnli). "
             "Provenance: LLM_INFERENCE (extractedBy=deep-analysis)._")
    return "\n".join(r)
