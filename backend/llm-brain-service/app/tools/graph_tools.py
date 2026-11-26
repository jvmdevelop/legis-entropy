import logging
import re
from dataclasses import dataclass

from app.clients.ports import DocumentPort, GraphPort, LawPort, SituationPort
from app.graph.actions import GraphActionService
from app.llm.tools import ToolRegistry
from app.ml.embeddings import Embedder
from app.ml.hybrid import HybridRanker
from app.ml.ner import NerModel
from app.ml.nli import NliModel
from app.ml.reranker import Reranker
from app.graph.deep import is_contradiction
from app.graph.laws import article_text, default_country, format_law_graph, law_line, rank, snippet
from app.schemas import CamelModel

log = logging.getLogger(__name__)

LINK_MIN_CONFIDENCE = 0.35
_NUM = re.compile(r"\d+(?:[-.]\d+)?")


class LawSearchArgs(CamelModel):
    query: str


class UserDocSearchArgs(CamelModel):
    query: str
    user_id: str | None = None
    document_id: str | None = None


class LawCodeArgs(CamelModel):
    code: str
    country: str | None = None


class AddLawArgs(CamelModel):
    graph_id: str
    code: str
    country: str | None = None


class FindAndAddArgs(CamelModel):
    graph_id: str
    query: str
    country: str | None = None
    limit: int | None = None


class ArticleLookupArgs(CamelModel):
    code: str | None = None
    country: str | None = None
    number: str | None = None


class ArticleSearchArgs(CamelModel):
    query: str
    country: str | None = None


class LinkClauseArgs(CamelModel):
    graph_id: str
    document_id: str
    law_code: str
    country: str | None = None
    article_number: str
    clause_ref: str | None = None
    document_snippet: str | None = None
    article_snippet: str | None = None
    confidence: float | None = None


class FlagArticleConflictArgs(CamelModel):
    graph_id: str
    country: str | None = None
    code_a: str
    number_a: str
    code_b: str
    number_b: str
    reason: str | None = None
    confidence: float | None = None


class FlagDocConflictArgs(CamelModel):
    graph_id: str
    document_id: str
    law_code: str
    country: str | None = None
    article_number: str
    clause_ref: str | None = None
    reason: str | None = None
    confidence: float | None = None
    document_snippet: str | None = None
    user_id: str | None = None


class LinkDocumentArgs(CamelModel):
    graph_id: str
    document_id: str
    user_id: str | None = None
    limit: int | None = None


DESCRIPTIONS = {
    "searchLaws": "Поиск в базе нормативно-правовых актов (законов) Казахстана и РФ. "
                  "Гибридный поиск: BGE-M3 dense + BM25 + RRF + реранкер.",
    "searchUserDocuments": "Поиск в загруженных пользователем документах (договорах, соглашениях). "
                           "Гибридный поиск: BGE-M3 + BM25 + реранкер.",
    "getLawGraph": "Получить полную информацию о законе, включая его связи с другими нормативными актами. "
                   "code - краткое или полное название/код закона (например, 'ГК', 'Гражданский кодекс', "
                   "'141-VII ЗРК'), country - страна (RK или RF)",
    "findRelatedLaws": "Найти все законы, связанные с заданным законом (ссылаются на него, изменяют его, "
                       "определяют его). Полезно для понимания правового контекста и зависимостей. "
                       "Порядок: связи графа + эмбеддинг-близость узлов.",
    "addLawToUserGraph": "Добавить закон в текущий граф пользователя (GraphView). "
                         "Параметр graphId должен совпадать с активным ID графа из системного контекста. "
                         "code — код или название закона, country — RK или RF (по умолчанию RK).",
    "findAndAddRelatedLawsToGraph": "Найти законы по смысловому запросу и автоматически добавить топ-K результатов "
                                    "в текущий граф пользователя. Полезно когда юрист просит «найди все законы про "
                                    "защиту прав потребителей и добавь в граф». graphId — ID активного графа из "
                                    "системного контекста, query — что искать, limit — сколько добавить "
                                    "(по умолчанию 5). Семантический поиск BGE-M3 + реранкер.",
    "findArticle": "Получить конкретную статью закона по её номеру. "
                   "code — код закона (например, 'ГК'), number — номер статьи (например, '350'), "
                   "country — RK или RF. Возвращает заголовок, тело статьи и метаданные источника (provenance). "
                   "Если номер не указан явно, он извлекается NER из текста (например, 'ст. 350 ГК').",
    "searchArticles": "Полнотекстовый поиск по статьям законов. Возвращает релевантные статьи с указанием закона. "
                      "query — текст/тема для поиска, country — RK или RF. "
                      "Гибридный поиск: BGE-M3 + BM25 + реранкер.",
    "linkDocumentClauseToArticle": "Связать конкретный пункт пользовательского документа с конкретной статьёй закона "
                                   "(clause-anchoring). Используй ПОСЛЕ того, как нашёл соответствующую статью и "
                                   "конкретный пункт документа. graphId — активный граф, documentId — ID документа, "
                                   "lawCode + articleNumber — куда привязываем, clauseRef — например 'п. 5.2', "
                                   "documentSnippet — точная цитата из документа, articleSnippet — цитата из статьи, "
                                   "confidence — уверенность 0..1 (для прозрачности provenance). Связь проверяется "
                                   "cosine BGE-M3 + cross-encoder; итоговая confidence вычисляется моделью.",
    "flagArticleConflict": "Зафиксировать конфликт между двумя статьями законов в текущем графе. "
                           "Используй когда обнаружил противоречие или коллизию норм между двумя статьями. "
                           "graphId — активный граф, codeA/numberA — первая статья (закон+номер), codeB/numberB — "
                           "вторая, reason — краткое объяснение конфликта на русском, confidence — уверенность 0..1. "
                           "Конфликт фиксируется только если NLI подтверждает противоречие (contradiction).",
    "flagDocumentArticleConflict": "Зафиксировать конфликт между пунктом пользовательского документа и статьёй закона. "
                                   "Используй когда обнаружил, что пункт договора/иска противоречит конкретной статье "
                                   "закона. graphId — активный граф, documentId — ID документа, lawCode + "
                                   "articleNumber — статья, clauseRef — например 'п. 3.2', reason — объяснение "
                                   "конфликта, confidence — 0..1, documentSnippet — цитата пункта (желательно). "
                                   "Конфликт фиксируется только если NLI подтверждает противоречие (contradiction).",
    "linkDocumentToLawsInGraph": "Найти законы, семантически связанные с загруженным пользователем документом, и "
                                 "соединить их с ним в графе. Используется когда юрист загружает свой документ "
                                 "(договор, иск) и хочет понять, какие законы РК с ним связаны. graphId — активный "
                                 "граф, documentId — ID документа уже в графе, userId — ID пользователя, limit — "
                                 "сколько связей создать (по умолчанию 5). Эмбеддинг документа + реранкер.",
}


@dataclass
class GraphTools:
    laws: LawPort
    documents: DocumentPort
    graph: GraphPort
    situations: SituationPort
    embedder: Embedder
    reranker: Reranker
    ranker: HybridRanker
    ner: NerModel
    nli: NliModel
    actions: GraphActionService
    top_k: int = 8

    async def search_laws(self, a: LawSearchArgs) -> list[dict]:
        log.info("Tool searchLaws called with query: %s", a.query)
        chunks = await self.laws.search(a.query)
        return [{**ch, "score": round(s, 4)} for ch, s in
                await rank(self.ranker, a.query, chunks, lambda x: x.get("text") or "", self.top_k)]

    async def search_user_documents(self, a: UserDocSearchArgs) -> list[dict]:
        log.info("Tool searchUserDocuments called for user %s with query: %s", a.user_id, a.query)
        chunks = await self.documents.search(a.query, a.user_id, a.document_id)
        return [{**ch, "score": round(s, 4)} for ch, s in
                await rank(self.ranker, a.query, chunks, lambda x: x.get("text") or "", self.top_k)]

    async def get_law_graph(self, a: LawCodeArgs) -> str:
        log.info("Tool getLawGraph called for %s in %s", a.code, a.country)
        try:
            g = await self.graph.law(a.code, a.country)
            if g:
                return format_law_graph(g)
        except Exception as e:
            log.debug("Direct lookup failed for %s, attempting search fallback. Error: %s", a.code, e)
        try:
            found = await self.graph.search_laws(a.code, a.country)
            if found:
                g = await self.graph.law(found[0]["code"], a.country)
                if g:
                    return format_law_graph(g)
        except Exception as e:
            log.warning("Fallback search or retry failed for %s: %s", a.code, e)
        return f"Закон '{a.code}' не найден в графе норм. Попробуйте уточнить название или код."

    async def find_related_laws(self, a: LawCodeArgs) -> str:
        log.info("Tool findRelatedLaws called for %s in %s", a.code, a.country)
        try:
            laws = await self.graph.related(a.code, a.country)
            if not laws:
                return f"Не найдены законы, связанные с {a.code}."
            try:
                base = await self.graph.law(a.code, a.country) or {}
                anchor = " ".join(str((base.get("law") or {}).get(k) or "") for k in ("title", "summary")) or a.code
                sims = await self.embedder.similarity(anchor, [f"{l.get('title') or ''} {l.get('summary') or ''}"
                                                                 for l in laws])
                laws = [l for _, l in sorted(zip(sims, laws), key=lambda p: -p[0])]
            except Exception as e:
                log.debug("embedding ordering of related laws skipped: %s", e)
            return "\n".join(law_line(l, with_summary=True) for l in laws)
        except Exception as e:
            log.warning("Failed to find related laws for %s: %s", a.code, e)
            return f"Ошибка при поиске связанных законов для {a.code}."

    async def add_law(self, a: AddLawArgs) -> str:
        log.info("Tool addLawToUserGraph called: graphId=%s, code=%s", a.graph_id, a.code)
        return (await self.actions.add_law_to_graph(a.graph_id, a.code, a.country)).report

    async def find_and_add(self, a: FindAndAddArgs) -> str:
        log.info("Tool findAndAddRelatedLawsToGraph called: graphId=%s, query=%s", a.graph_id, a.query)
        return (await self.actions.find_and_add_laws(a.graph_id, a.query, a.country, a.limit or 5)).report

    async def link_document_to_laws(self, a: LinkDocumentArgs) -> str:
        log.info("Tool linkDocumentToLawsInGraph: graphId=%s, doc=%s", a.graph_id, a.document_id)
        return (await self.actions.link_document_to_laws(a.graph_id, a.document_id, a.user_id, a.limit or 5,
                                                         "RK")).report

    async def _resolve_article_ref(self, code: str | None, number: str | None) -> tuple[str | None, str | None]:
        if number and _NUM.fullmatch(number.strip()):
            return code, number.strip()
        text = " ".join(x for x in (number, code) if x)
        try:
            ents = await self.ner.extract(f"ст. {text}" if text and text[0].isdigit() else text)
        except Exception as e:
            log.debug("NER failed for article ref '%s': %s", text, e)
            ents = []
        art = next((e for e in ents if e.label == "article" and e.attrs.get("number")), None)
        if art:
            return (code if code and not _NUM.search(code) else art.attrs.get("lawCode") or code), art.attrs["number"]
        m = _NUM.search(text)
        return code, m.group(0) if m else None

    async def find_article(self, a: ArticleLookupArgs) -> str:
        code, number = await self._resolve_article_ref(a.code, a.number)
        country = default_country(a.country)
        log.info("Tool findArticle called: %s ст. %s (%s)", code, number, country)
        if not code or not number:
            return f"Не удалось определить закон и номер статьи из запроса ({a.code or ''} {a.number or ''})."
        try:
            art = await self.graph.article(code, number, country)
        except Exception as e:
            log.warning("Failed to retrieve article %s for law %s: %s", number, code, e)
            return f"Ошибка при получении статьи {number} закона {code}."
        if not art:
            return f"Статья {number} закона {code} не найдена."
        out = f"**Ст. {art.get('number')} {art.get('lawCode')}" + (f" — {art['title']}" if art.get("title") else "")
        out += "**\n\n" + (f"{art['body']}\n\n" if art.get("body") else "")
        if art.get("source"):
            out += f"_Источник: {art['source']}"
            if art.get("confidence") is not None:
                out += f", confidence={art['confidence']}"
            if art.get("verifiedBy"):
                out += f", verified by {art['verifiedBy']}"
            out += "_"
        return out

    async def search_articles(self, a: ArticleSearchArgs) -> str:
        country = default_country(a.country)
        log.info("Tool searchArticles called: query='%s' country=%s", a.query, country)
        try:
            arts = await self.graph.search_articles(a.query, country)
            if not arts:
                return f"Ничего не найдено по запросу «{a.query}» среди статей."
            ranked = await rank(self.ranker, a.query, arts, article_text, 10)
            return "\n".join(f"- **{x.get('lawCode')} ст. {x.get('number')}**" + (f": {x['title']}" if x.get("title") else "")
                             for x, _ in ranked)
        except Exception as e:
            log.warning("Failed to search articles for query '%s': %s", a.query, e)
            return "Ошибка при поиске статей по запросу."

    async def _article_body(self, code: str, number: str, country: str) -> str | None:
        try:
            art = await self.graph.article(code, number, country)
            return article_text(art) if art else None
        except Exception as e:
            log.debug("article %s ст.%s lookup failed: %s", code, number, e)
            return None

    async def _clause_confidence(self, clause: str, article: str) -> float:
        cos = max(0.0, (await self.embedder.similarity(clause, [article]))[0])
        if getattr(self.reranker, "name", "") == "bm25":
            return round(cos, 3)
        rr = (await self.reranker.rerank(clause, [article], 1) or [(0, 0.0)])[0][1]
        return round((cos + max(0.0, min(1.0, rr))) / 2, 3)

    async def link_clause(self, a: LinkClauseArgs) -> str:
        log.info("Tool linkDocumentClauseToArticle: doc=%s -> %s ст. %s (clause %s)", a.document_id, a.law_code,
                 a.article_number, a.clause_ref)
        country = default_country(a.country)
        confidence = a.confidence
        article = a.article_snippet or (await self._article_body(a.law_code, a.article_number, country) if
                                        a.document_snippet else None)
        if a.document_snippet and article:
            try:
                confidence = await self._clause_confidence(a.document_snippet[:2000], article[:2000])
            except Exception as e:
                log.debug("clause verification failed, keeping model confidence: %s", e)
            if confidence is not None and confidence < LINK_MIN_CONFIDENCE:
                return (f"Связь не создана: пункт документа слабо соответствует ст. {a.article_number} закона "
                        f"{a.law_code} (confidence={confidence:.2f}). Уточни пункт или статью.")
        try:
            await self.graph.link_clause({
                "graphId": a.graph_id, "documentId": a.document_id, "lawCode": a.law_code, "country": country,
                "articleNumber": a.article_number, "clauseRef": a.clause_ref, "documentSnippet": a.document_snippet,
                "articleSnippet": a.article_snippet or (snippet(article, 220) if article else None),
                "extractedBy": "llm-brain", "confidence": confidence})
        except Exception as e:
            log.warning("Failed to link document clause to article: %s", e)
            return "Не удалось привязать пункт к статье. Проверь существование статьи и документа в графе."
        return (f"Привязан {a.clause_ref or 'пункт документа'} к ст. {a.article_number} закона {a.law_code}."
                + (f" (confidence={confidence:.2f})" if confidence is not None else ""))

    async def _nli(self, premise: str, hypothesis: str):
        return (await self.nli.predict([(premise[:1500], hypothesis[:1500])]))[0]

    async def flag_article_conflict(self, a: FlagArticleConflictArgs) -> str:
        log.info("Tool flagArticleConflict: %sст.%s vs %sст.%s graph=%s", a.code_a, a.number_a, a.code_b, a.number_b,
                 a.graph_id)
        country = default_country(a.country)
        body_a = await self._article_body(a.code_a, a.number_a, country)
        body_b = await self._article_body(a.code_b, a.number_b, country)
        if not body_a or not body_b:
            return "Не удалось зафиксировать конфликт — статьи не найдены в базе."
        try:
            r = await self._nli(body_a, body_b)
        except Exception as e:
            log.warning("NLI check failed: %s", e)
            return "Конфликт не зафиксирован: не удалось выполнить NLI-проверку противоречия."
        if not is_contradiction(r):
            return (f"Конфликт не подтверждён NLI-проверкой ({a.code_a} ст.{a.number_a} ↔ {a.code_b} ст.{a.number_b}: "
                    f"contradiction={r.contradiction:.2f}, вердикт {r.label}) — не зафиксирован.")
        try:
            await self.situations.flag_article_conflict({
                "graphId": a.graph_id, "country": country, "codeA": a.code_a, "numberA": a.number_a,
                "codeB": a.code_b, "numberB": a.number_b, "reason": a.reason, "confidence": round(r.contradiction, 3)})
        except Exception as e:
            log.warning("Failed to flag article conflict: %s", e)
            return "Не удалось зафиксировать конфликт — статьи не найдены в базе."
        return (f"Конфликт зафиксирован: {a.code_a} ст.{a.number_a} ↔ {a.code_b} ст.{a.number_b}"
                + (f" — {a.reason}" if a.reason else "") + f" (NLI contradiction={r.contradiction:.2f})")

    async def _clause_text(self, a: FlagDocConflictArgs, article: str) -> str | None:
        if a.document_snippet and a.document_snippet.strip():
            return a.document_snippet
        query = " ".join(x for x in (a.clause_ref, a.reason) if x) or article[:300]
        try:
            chunks = await self.documents.search(query, a.user_id, a.document_id)
        except Exception as e:
            log.debug("document chunk search failed: %s", e)
            return None
        best = await rank(self.ranker, article[:1000], chunks, lambda x: x.get("text") or "", 1)
        return (best[0][0].get("text") or None) if best else None

    async def flag_document_conflict(self, a: FlagDocConflictArgs) -> str:
        log.info("Tool flagDocumentArticleConflict: doc=%s clause=%s -> %sст.%s graph=%s", a.document_id,
                 a.clause_ref, a.law_code, a.article_number, a.graph_id)
        country = default_country(a.country)
        article = await self._article_body(a.law_code, a.article_number, country)
        clause = await self._clause_text(a, article) if article else None
        if not article or not clause:
            return "Не удалось зафиксировать конфликт — документ или статья не найдены."
        try:
            r = await self._nli(article, clause)
        except Exception as e:
            log.warning("NLI check failed: %s", e)
            return "Конфликт не зафиксирован: не удалось выполнить NLI-проверку противоречия."
        if not is_contradiction(r):
            return (f"Конфликт не подтверждён NLI-проверкой (документ ({a.clause_ref or '?'}) ↔ {a.law_code} "
                    f"ст.{a.article_number}: contradiction={r.contradiction:.2f}, вердикт {r.label}) — не зафиксирован.")
        try:
            await self.situations.flag_document_conflict({
                "graphId": a.graph_id, "documentId": a.document_id, "lawCode": a.law_code, "country": country,
                "articleNumber": a.article_number, "clauseRef": a.clause_ref, "reason": a.reason,
                "confidence": round(r.contradiction, 3)})
        except Exception as e:
            log.warning("Failed to flag document-article conflict: %s", e)
            return "Не удалось зафиксировать конфликт — документ или статья не найдены."
        return (f"Конфликт зафиксирован: документ ({a.clause_ref or '?'}) ↔ {a.law_code} ст.{a.article_number}"
                + (f" — {a.reason}" if a.reason else "") + f" (NLI contradiction={r.contradiction:.2f})")


def register_graph_tools(tools: ToolRegistry, t: GraphTools) -> GraphTools:
    for name, args, fn in [
        ("searchLaws", LawSearchArgs, t.search_laws),
        ("searchUserDocuments", UserDocSearchArgs, t.search_user_documents),
        ("getLawGraph", LawCodeArgs, t.get_law_graph),
        ("findRelatedLaws", LawCodeArgs, t.find_related_laws),
        ("addLawToUserGraph", AddLawArgs, t.add_law),
        ("findAndAddRelatedLawsToGraph", FindAndAddArgs, t.find_and_add),
        ("findArticle", ArticleLookupArgs, t.find_article),
        ("searchArticles", ArticleSearchArgs, t.search_articles),
        ("linkDocumentClauseToArticle", LinkClauseArgs, t.link_clause),
        ("flagArticleConflict", FlagArticleConflictArgs, t.flag_article_conflict),
        ("flagDocumentArticleConflict", FlagDocConflictArgs, t.flag_document_conflict),
        ("linkDocumentToLawsInGraph", LinkDocumentArgs, t.link_document_to_laws),
    ]:
        tools.register(name, DESCRIPTIONS[name], args, fn)
    return t
