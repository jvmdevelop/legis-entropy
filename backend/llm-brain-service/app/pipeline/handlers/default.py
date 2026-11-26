import asyncio
import json
import logging
import re
from typing import AsyncIterator

from app.ml.ner import NerModel
from app.pipeline.context import DocumentContext
from app.pipeline.hallucination import UNGROUNDED_DISCLAIMER, AnswerPostProcessor, HallucinationDetector
from app.legal.glossary import render as render_glossary
from app.pipeline.quotes import QuoteChecker
from app.pipeline.retrieval import RetrievalRouter
from app.pipeline.tool_policy import tools_for
from app.pipeline.types import ContextPlan, RetrievalMode, TaskType
from app.prompt.builder import BrainPromptBuilder
from app.schemas import BrainRequest, BrainResponse, EditSuggestResponse
from app.sse import Sse

from app.clients.ports import DocumentPort
from app.ports import EditSuggester, LawGraphWriter, LawNameLinker
from app.llm.client import ChatModel

log = logging.getLogger(__name__)

MAX_AUTO_GRAPH_LAWS = 8
MAX_NAME_LOOKUPS = 5
DEFAULT_COUNTRY = "RK"
LAW_CODE_FORMAT = re.compile(r"^\d{1,4}-[IVXL]{1,6}$")
EDITS_TIMEOUT_S = 180
EDIT_TASKS = {TaskType.DOCUMENT_AUDIT}
EDITS_EVENT = "document-edit-proposals"
GLOSSARY_SCAN = 20000


def document_unavailable_message(reason: str | None) -> str:
    return (f"Не удалось обработать прикреплённый документ: {reason} Попробуйте отправить вопрос ещё раз через "
            "несколько секунд (индексация занимает время) или прикрепите документ повторно.")


def _thinking(payload: dict) -> Sse:
    return Sse(json.dumps(payload, ensure_ascii=False, separators=(",", ":")), "thinking")


class DefaultBrainHandler:
    def __init__(self, router: RetrievalRouter, prompts: BrainPromptBuilder, llm: ChatModel, post: AnswerPostProcessor,
                 detector: HallucinationDetector, graph_actions: LawGraphWriter | None = None,
                 ner: NerModel | None = None, linker: LawNameLinker | None = None,
                 edits: EditSuggester | None = None, documents: DocumentPort | None = None, glossary=None):
        self.router = router
        self.prompts = prompts
        self.llm = llm
        self.post = post
        self.detector = detector
        self._graph_actions = graph_actions
        self.ner = ner
        self.linker = linker
        self.edits, self.documents = edits, documents
        self.glossary = glossary

    def supports(self, task: TaskType) -> bool:
        return True

    async def _check_quotes(self, req: BrainRequest, answer: str) -> str:
        if not req.has_document or self.documents is None or not answer:
            return answer
        try:
            text = await self.documents.text(req.document_id, req.user_id)
        except Exception as e:
            log.warning("Quote check: document %s unavailable: %s", req.document_id, e)
            return answer
        checked, report = QuoteChecker(text or "").check(answer)
        if report.fixed or report.not_found or report.clause_fixed:
            log.info("Quote check %s: exact=%d fixed=%d not_found=%d clause_fixed=%d", req.document_id,
                     report.exact, report.fixed, report.not_found, report.clause_fixed)
        return checked

    async def _edit_proposals(self, req: BrainRequest) -> EditSuggestResponse | None:
        try:
            text = await self.documents.text(req.document_id, req.user_id)
            if not text or not text.strip():
                return None
            proposals, summary = await self.edits.suggest_for_text(text)
            return EditSuggestResponse(document_id=req.document_id, summary=summary, proposals=proposals)
        except Exception as e:
            log.warning("Edit proposals for document %s failed: %s", req.document_id, e)
            return None

    def _start_edits(self, req: BrainRequest, plan: ContextPlan) -> "asyncio.Task | None":
        if plan.task not in EDIT_TASKS or not req.has_document or self.edits is None or self.documents is None:
            return None
        return asyncio.create_task(self._edit_proposals(req))

    @staticmethod
    async def _finish_edits(task: "asyncio.Task | None") -> Sse | None:
        if task is None:
            return None
        try:
            res = await asyncio.wait_for(task, EDITS_TIMEOUT_S)
        except Exception as e:
            log.warning("Edit proposals not ready: %s", e)
            return None
        return Sse.of(res, EDITS_EVENT) if res and res.proposals else None

    @staticmethod
    def _graph_eligible(req: BrainRequest, plan: ContextPlan) -> bool:
        return req.has_graph and plan.retrieval in (RetrievalMode.LAW, RetrievalMode.HYBRID_LEGAL)

    async def _add_to_graph(self, req: BrainRequest, codes: list[str]) -> list[str]:
        actions = self._graph_actions
        codes = list(dict.fromkeys(c for c in codes if c and c.strip()))[:MAX_AUTO_GRAPH_LAWS]
        if not codes or actions is None:
            return []
        try:
            added = list(await actions.add_law_codes(req.graph_id, codes, DEFAULT_COUNTRY) or [])
            log.info("Auto-added %d law(s) to graph %s: %s", len(added), req.graph_id, added)
            return added
        except Exception as e:
            log.warning("Auto-adding laws to graph %s failed: %s", req.graph_id, e)
            return []

    async def add_retrieved_laws(self, req: BrainRequest, plan: ContextPlan, ctx: DocumentContext | None) -> list[str]:
        if not self._graph_eligible(req, plan) or ctx is None or not ctx.has_citations:
            return []
        return await self._add_to_graph(req, [c.law_code for c in ctx.citations if c.law_code])

    async def add_answer_laws(self, req: BrainRequest, plan: ContextPlan, answer: str,
                              already: list[str], ctx: DocumentContext | None) -> list[str]:
        if not self._graph_eligible(req, plan) or self.ner is None or len(already) >= MAX_AUTO_GRAPH_LAWS:
            return []
        try:
            ents = await self.ner.extract(answer)
        except Exception as e:
            log.debug("NER on answer failed: %s", e)
            return []
        known = set(already) | {c.law_code for c in (ctx.citations if ctx else []) if c.law_code}
        codes = [(e.attrs.get("code") or e.attrs.get("lawCode")) for e in ents if e.label in ("law", "article")]
        codes += await self._link_names(ents)
        codes = [c for c in dict.fromkeys(codes) if c and LAW_CODE_FORMAT.match(c) and c not in known]
        return await self._add_to_graph(req, codes[:MAX_AUTO_GRAPH_LAWS - len(already)])

    async def _link_names(self, ents) -> list[str]:
        if self.linker is None:
            return []
        names = [e.text if e.label == "law" else e.attrs.get("lawHint") for e in ents
                 if e.label in ("law", "article") and not (e.attrs.get("code") or e.attrs.get("lawCode"))]
        codes = []
        for name in list(dict.fromkeys(n for n in names if n and n.strip()))[:MAX_NAME_LOOKUPS]:
            try:
                linked = await self.linker.link(name, DEFAULT_COUNTRY)
            except Exception as e:
                log.debug("law linking %r failed: %s", name, e)
                continue
            if linked:
                log.info("Linked law name %r -> %s (%.2f)", name, linked.code, linked.score)
                codes.append(linked.code)
        return codes

    def _doc_unavailable(self, req: BrainRequest, plan: ContextPlan, ctx: DocumentContext) -> bool:
        return req.has_document and ctx.unavailable and plan.retrieval != RetrievalMode.NONE

    async def _retrieve(self, req: BrainRequest, plan: ContextPlan) -> DocumentContext:
        try:
            ctx = await self.router.retrieve(req, plan)
        except Exception as e:
            log.warning("Retrieval failed: %s", e)
            return DocumentContext.missing("Retrieval-контекст не использовался для этого запроса.")
        return await self._with_glossary(req, ctx)

    async def _with_glossary(self, req: BrainRequest, ctx: DocumentContext) -> DocumentContext:
        if self.glossary is None or ctx.unavailable:
            return ctx
        try:
            terms = await self.glossary.relevant(f"{req.message}\n{ctx.text[:GLOSSARY_SCAN]}")
        except Exception as e:
            log.warning("Glossary lookup failed: %s", e)
            return ctx
        block = render_glossary(terms)
        return DocumentContext(f"{ctx.text}\n\n{block}", ctx.citations) if block else ctx

    async def handle(self, req: BrainRequest, plan: ContextPlan) -> BrainResponse:
        ctx = await self._retrieve(req, plan)
        if self._doc_unavailable(req, plan, ctx):
            return BrainResponse(content=document_unavailable_message(ctx.unavailable_reason), tokens=0)
        prompt = await self.prompts.build_user_prompt(req, ctx, plan)
        raw = await self.llm.answer(prompt, req.history, tools=tools_for(req, plan))
        processed = await self._check_quotes(req, await self.post.process(raw.content, plan, ctx))
        added = await self.add_retrieved_laws(req, plan, ctx)
        added += await self.add_answer_laws(req, plan, raw.content or "", added, ctx)
        if added:
            processed += "\n\n---\nДобавлено в граф: " + ", ".join(added)
        return BrainResponse(content=processed, tokens=raw.tokens)

    async def stream(self, req: BrainRequest, plan: ContextPlan) -> AsyncIterator[Sse]:
        yield _thinking({"step": "classify", "task": plan.task.value, "retrieval": plan.retrieval.value,
                         "profession": plan.professions[0].value})
        ctx = await self._retrieve(req, plan)
        yield _thinking({"step": "retrieve", "found": len(ctx.citations)})
        if self._doc_unavailable(req, plan, ctx):
            yield Sse(document_unavailable_message(ctx.unavailable_reason))
            return
        added = await self.add_retrieved_laws(req, plan, ctx)
        if added:
            yield _thinking({"step": "graph", "added": len(added)})
        edits_task = self._start_edits(req, plan)
        prompt = await self.prompts.build_user_prompt(req, ctx, plan)
        log.info("Starting streaming for user %s with task %s", req.user_id, plan.task.value)
        parts: list[str] = []
        try:
            async for chunk in self.llm.stream(prompt, req.history, tools=tools_for(req, plan)):
                if chunk:
                    parts.append(chunk)
                    yield Sse(chunk)
        except Exception as e:
            log.error("Pipeline streaming error: %s", e)
            if edits_task:
                edits_task.cancel()
            yield Sse(f"Ошибка при получении ответа от ИИ: {e}", "error")
            return
        final = "".join(parts)
        try:
            processed = await self._check_quotes(req, await self.post.process(final, plan, ctx))
            if (not self.detector.has_grounding_context(ctx) and not req.has_document
                    and processed == final.strip() and self.detector.has_law_references(processed)):
                processed += UNGROUNDED_DISCLAIMER
            if processed != final.strip():
                yield Sse(processed, "redaction")
            more = await self.add_answer_laws(req, plan, final, added, ctx)
            if more:
                yield _thinking({"step": "graph", "added": len(more)})
        except Exception as e:
            log.warning("Post-processing failed: %s", e)
        if edits_task:
            yield _thinking({"step": "edits"})
            if (ev := await self._finish_edits(edits_task)) is not None:
                yield ev
        yield Sse("", "done")
        log.info("Streaming completed successfully for user %s", req.user_id)
