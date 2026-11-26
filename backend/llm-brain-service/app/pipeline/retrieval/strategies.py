import asyncio
import inspect
import logging
from typing import Any, Protocol

from app.ml.embeddings import Embedder
from app.ml.hybrid import HybridRanker
from app.pipeline.context import DocumentContext, DocumentContextFormatter, to_chunks
from app.pipeline.retrieval.rewriter import QueryRewriter
from app.pipeline.types import ContextPlan, RetrievalMode, TaskType
from app.schemas import BrainRequest, RetrievalChunk
from app.clients.ports import DocumentPort
from app.clients.ports import LawPort
from app.clients.ports import GraphPort
from app.ports import ReferenceReports

log = logging.getLogger(__name__)

DEFAULT_COUNTRY = "RK"
FULL_TEXT_CHAR_LIMIT = 60_000
PER_QUESTION_HITS = 3
MAX_MERGED_HITS = 20
MAX_GRAPH_HOPS = 2
GRAPH_FANOUT = 3
MAX_GRAPH_LINES = 12


async def maybe_await(v: Any) -> Any:
    return await v if inspect.isawaitable(v) else v


class RetrievalStrategy(Protocol):
    mode: RetrievalMode

    async def retrieve(self, req: BrainRequest, plan: ContextPlan) -> DocumentContext: ...


class NoRetrievalStrategy:
    mode = RetrievalMode.NONE

    async def retrieve(self, req: BrainRequest, plan: ContextPlan) -> DocumentContext:
        return DocumentContext.missing("Retrieval-контекст не использовался для этого запроса.")


async def _rerank(ranker: HybridRanker | None, query: str, chunks: list[RetrievalChunk],
                  top_k: int) -> list[RetrievalChunk]:
    if ranker is None or len(chunks) <= 1:
        return chunks[:top_k]
    try:
        ranked = await ranker.rank(query, chunks, lambda c: c.text, top_k=top_k)
        return [r.item for r in ranked] or chunks[:top_k]
    except Exception as e:
        log.debug("rerank failed (%s) - keeping service order", e)
        return chunks[:top_k]


class UserDocumentRetrievalStrategy:
    mode = RetrievalMode.USER_DOCUMENT

    def __init__(self, documents: DocumentPort, formatter: DocumentContextFormatter, ranker: HybridRanker | None = None,
                 reference_verifier: ReferenceReports | None = None, top_k: int = 8):
        self.documents = documents
        self.formatter = formatter
        self.ranker = ranker
        self.verifier = reference_verifier
        self.top_k = top_k

    async def _full_text(self, req: BrainRequest) -> str | None:
        try:
            return await self.documents.text(req.document_id, req.user_id)
        except Exception as e:
            log.warning("Could not load full text for user %s document %s: %s", req.user_id, req.document_id, e)
            return None

    async def _with_report(self, ctx: DocumentContext, full_text: str | None) -> DocumentContext:
        if ctx.unavailable or not full_text or not full_text.strip() or self.verifier is None:
            return ctx
        try:
            report = await maybe_await(self.verifier.build_report(full_text, compact=False))
        except Exception as e:
            log.warning("Reference check failed for document context: %s", e)
            return ctx
        if not report or not str(report).strip():
            return ctx
        return DocumentContext(ctx.text + "\n\n" + str(report), ctx.citations)

    async def retrieve(self, req: BrainRequest, plan: ContextPlan) -> DocumentContext:
        if not req.has_document:
            return DocumentContext.missing("Документ не прикреплен.")
        full_text = await self._full_text(req)
        if plan.task == TaskType.SUMMARY and full_text and full_text.strip() and len(full_text) <= FULL_TEXT_CHAR_LIMIT:
            return await self._with_report(DocumentContext.of(full_text), full_text)
        try:
            chunks = to_chunks(await self.documents.search(req.message, req.user_id, req.document_id))
        except Exception as e:
            log.warning("Could not load document context for user %s document %s: %s",
                        req.user_id, req.document_id, e)
            return DocumentContext.missing("Не удалось загрузить контекст документа из сервиса документов.")
        chunks = await _rerank(self.ranker, req.message, chunks, self.top_k)
        return await self._with_report(self.formatter.format(chunks), full_text)


def _chunk_key(c: RetrievalChunk) -> str:
    if c.document_id is not None and c.chunk_index is not None:
        return f"{c.document_id}#{c.chunk_index}"
    return str(hash(c.text or ""))


class LawRetrievalStrategy:
    mode = RetrievalMode.LAW

    def __init__(self, laws: LawPort, graph: GraphPort, formatter: DocumentContextFormatter, rewriter: QueryRewriter,
                 ranker: HybridRanker | None = None, embedder: Embedder | None = None, top_k: int = 8):
        self.laws = laws
        self.graph = graph
        self.formatter = formatter
        self.rewriter = rewriter
        self.ranker = ranker
        self.embedder = embedder
        self.top_k = top_k

    async def _search(self, query: str) -> list[RetrievalChunk]:
        chunks = to_chunks(await self.laws.search(query, DEFAULT_COUNTRY))
        return chunks or to_chunks(await self.laws.search(query))

    async def _search_ranked(self, query: str, top_k: int) -> list[RetrievalChunk]:
        try:
            return await _rerank(self.ranker, query, await self._search(query), top_k)
        except Exception as e:
            log.debug("Sub-question search failed for '%s': %s", query, e)
            return []

    async def search_each(self, queries: list[str]) -> list[RetrievalChunk]:
        per_query = await asyncio.gather(*(self._search_ranked(q, PER_QUESTION_HITS) for q in queries))
        merged: dict[str, RetrievalChunk] = {}
        for rank in range(PER_QUESTION_HITS):
            for hits in per_query:
                if rank < len(hits) and len(merged) < MAX_MERGED_HITS:
                    merged.setdefault(_chunk_key(hits[rank]), hits[rank])
        log.info("Multi-question law retrieval: %d queries -> %d chunks", len(queries), len(merged))
        return list(merged.values())

    async def graph_context(self, query: str, chunks: list[RetrievalChunk], country: str = DEFAULT_COUNTRY) -> str:
        primary = list(dict.fromkeys(str(c.metadata.get("lawCode")) for c in chunks
                                     if c.metadata and c.metadata.get("lawCode")
                                     and str(c.metadata.get("lawCode")).strip()))
        if not primary or self.graph is None:
            return ""
        seen = set(primary)
        found: list[tuple[str, str | None]] = []
        frontier = primary[:GRAPH_FANOUT]
        for _ in range(MAX_GRAPH_HOPS):
            async def related(code: str) -> list[dict]:
                try:
                    return await self.graph.related(code, country) or []
                except Exception as e:
                    log.debug("Graph hop failed for %s: %s", code, e)
                    return []
            nxt: list[str] = []
            for laws in await asyncio.gather(*(related(c) for c in frontier)):
                for law in laws:
                    code = law.get("code") if isinstance(law, dict) else None
                    if not code or code in seen:
                        continue
                    seen.add(code)
                    found.append((code, law.get("title")))
                    nxt.append(code)
            frontier = nxt[:GRAPH_FANOUT]
            if not frontier:
                break
        if not found:
            return ""
        if self.embedder is not None and len(found) > MAX_GRAPH_LINES:
            try:
                sims = await self.embedder.similarity(query, [f"{c} {t or ''}" for c, t in found])
                found = [f for _, f in sorted(zip(sims, found), key=lambda x: -x[0])]
            except Exception as e:
                log.debug("graph node ranking failed: %s", e)
        return "".join(f"• {c}" + (f" — {t}" if t else "") + "\n" for c, t in found[:MAX_GRAPH_LINES])

    async def retrieve(self, req: BrainRequest, plan: ContextPlan) -> DocumentContext:
        try:
            queries = await self.rewriter.rewrite(req.message)
            if len(queries) == 1:
                chunks = await _rerank(self.ranker, queries[0], await self._search(queries[0]), self.top_k)
            else:
                chunks = await self.search_each(queries)
            primary = self.formatter.format(chunks)
            graph = await self.graph_context(req.message, chunks)
            if not graph.strip():
                return primary
            return DocumentContext.of(primary.text + "\n\n--- Смежные нормы (граф) ---\n" + graph, primary.citations)
        except Exception as e:
            log.warning("Could not load law context: %s", e)
            return DocumentContext.missing("Не удалось загрузить контекст нормативно-правовых актов.")


class HybridLegalRetrievalStrategy:
    mode = RetrievalMode.HYBRID_LEGAL

    def __init__(self, document: UserDocumentRetrievalStrategy, law: LawRetrievalStrategy):
        self.document = document
        self.law = law

    async def retrieve(self, req: BrainRequest, plan: ContextPlan) -> DocumentContext:
        doc, law = await asyncio.gather(self.document.retrieve(req, plan), self.law.retrieve(req, plan))
        return DocumentContext.of(f"Контекст документа:\n{doc.text}\n\nКонтекст законов:\n{law.text}\n",
                                  [*doc.citations, *law.citations])


class RetrievalRouter:
    def __init__(self, strategies: list[RetrievalStrategy]):
        self._by_mode = {s.mode: s for s in strategies}

    async def retrieve(self, req: BrainRequest, plan: ContextPlan) -> DocumentContext:
        strategy = self._by_mode.get(plan.retrieval)
        if strategy is None:
            raise LookupError(f"No retrieval strategy registered for {plan.retrieval}")
        return await strategy.retrieve(req, plan)
