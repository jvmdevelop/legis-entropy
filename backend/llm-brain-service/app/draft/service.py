import json
import logging
import uuid
from typing import AsyncIterator, Awaitable, Callable

from pydantic import BaseModel, Field

from app.clients.ports import GraphPort, SituationPort, TemplatePort
from app.graph.laws import law_text, rank
from app.graph.pump import EventPump
from app.ml.hybrid import HybridRanker
from app.schemas import BrainRequest, BrainResponse, CamelModel
from app.sse import Sse
from app.llm.client import Completer

log = logging.getLogger(__name__)

Notify = Callable[[str, str], Awaitable[None] | None]

DRAFT_SYSTEM = """Ты — профессиональный юрист в Республике Казахстан.
Составь юридический документ строго по запросу пользователя.
Используй актуальное законодательство РК.
Поле body — ТОЛЬКО текст самого документа в формате markdown — без вступлений, объяснений и комментариев.

ВАЖНО: Все поля, которые пользователь должен заполнить (ФИО, дата, адрес, сумма, наименование суда и т.д.),
обозначай токеном в формате {{название_поля}}, например:
{{фио_истца}}, {{дата}}, {{адрес_ответчика}}, {{сумма_ущерба}}, {{наименование_суда}}.
Не вставляй реальные имена и данные — только токены.
В variables укажи значения только тех токенов, которые пользователь явно сообщил в запросе."""

DRAFT_PLAIN_PROMPT = """Ты — профессиональный юрист в Республике Казахстан.
Составь юридический документ строго по запросу пользователя.
Используй актуальное законодательство РК.
Ответь ТОЛЬКО текстом самого документа в формате markdown — без вступлений, объяснений и комментариев.

ВАЖНО: Все поля, которые пользователь должен заполнить (ФИО, дата, адрес, сумма, наименование суда и т.д.),
обозначай токеном в формате {{название_поля}}, например:
{{фио_истца}}, {{дата}}, {{адрес_ответчика}}, {{сумма_ущерба}}, {{наименование_суда}}.
Не вставляй реальные имена и данные — только токены."""


class DraftDocument(BaseModel):
    title: str = Field("", description="Краткое название документа")
    body: str = Field(description="Текст документа в markdown с токенами {{название_поля}}")
    variables: dict[str, str] = Field(default_factory=dict,
                                      description="Значения токенов, явно указанные пользователем (название_поля -> значение)")


class DraftResult(CamelModel):
    template_id: str | None = None
    generated_doc_id: str | None = None
    situation_id: str | None = None
    law_codes: list[str] = []


class DraftStepNotification(CamelModel):
    step: str
    message: str


def short_title(message: str | None) -> str:
    if not message or not message.strip():
        return "Документ"
    m = message.strip()
    for i, ch in enumerate(m[:80]):
        if ch in ".\n":
            return m[:i].strip()
    return m if len(m) <= 80 else m[:77] + "..."


def confirmation_text(r: DraftResult) -> str:
    out = "**Документ подготовлен.**\n\n"
    if r.law_codes:
        out += f"Применимые нормы: **{', '.join(r.law_codes)}**\n\n"
    if r.situation_id:
        out += "Ситуация создана в графе, законы привязаны.\n\n"
    if r.template_id:
        out += ("Шаблон сохранён в разделе **Шаблоны** — вы можете его отредактировать "
                "и повторно сгенерировать документ через кнопку «Применить».\n\n")
    if r.generated_doc_id:
        out += "Готовый документ доступен в графе — откройте жёлтый узел рядом с ситуацией."
    return out


class DocumentDraftService:
    def __init__(self, llm: Completer, graph: GraphPort, situations: SituationPort, templates: TemplatePort,
                 ranker: HybridRanker):
        self.llm, self.graph, self.situations, self.templates, self.ranker = llm, graph, situations, templates, ranker

    async def draft(self, req: BrainRequest) -> BrainResponse:
        try:
            return BrainResponse(content=confirmation_text(await self.execute(req)), tokens=0)
        except Exception as e:
            log.error("Document draft failed for user %s: %s", req.user_id, e, exc_info=True)
            return BrainResponse(content=f"Не удалось подготовить документ: {e}", tokens=0)

    async def stream_draft(self, req: BrainRequest) -> AsyncIterator[Sse]:
        pump = EventPump()
        notify = lambda step, msg: pump.emit(Sse.thinking(  # noqa: E731
            DraftStepNotification(step=step, message=msg).model_dump(by_alias=True)))
        try:
            async for ev in pump.run(lambda: self.execute(req, notify)):
                yield ev
        except Exception as e:
            log.error("Document draft stream failed for user %s: %s", req.user_id, e, exc_info=True)
            yield Sse.of(f"Ошибка при подготовке документа: {e}", "error")
            return
        yield Sse.of(pump.result, "document-draft-result")
        yield Sse.of(confirmation_text(pump.result))

    async def execute(self, req: BrainRequest, notify: Notify | None = None) -> DraftResult:
        async def step(name: str, msg: str):
            if notify:
                r = notify(name, msg)
                if r is not None and hasattr(r, "__await__"):
                    await r

        await step("draft-laws", "Ищу применимые нормы...")
        law_codes = await self.find_relevant_laws(req.message)
        log.info("Draft: found %d law(s) for user %s", len(law_codes), req.user_id)

        situation_id = None
        if req.has_graph:
            await step("draft-situation", "Создаю ситуацию в графе...")
            situation_id = await self._create_situation(req)
            if situation_id:
                await step("draft-link", "Привязываю законы к ситуации...")
                await self._link_laws(req.graph_id, situation_id, law_codes)

        await step("draft-generate", "Генерирую текст документа...")
        doc = await self.generate(req.message, law_codes)

        await step("draft-template", "Создаю шаблон...")
        title = short_title(req.message)
        template_id = await self._create_template(req.user_id, doc.body, law_codes, req.message)

        generated_id = None
        if template_id:
            await step("draft-save", "Сохраняю документ...")
            generated_id = await self.templates.render(template_id, req.user_id, title, doc.variables,
                                                       req.graph_id, situation_id)
        if generated_id and situation_id and req.has_graph:
            await self.situations.set_generated_doc(req.graph_id, situation_id, generated_id, title)
        return DraftResult(template_id=template_id, generated_doc_id=generated_id, situation_id=situation_id,
                           law_codes=law_codes)

    async def find_relevant_laws(self, message: str) -> list[str]:
        try:
            laws = [l for l in await self.graph.search_laws(message, "RK") if l.get("code")]
        except Exception as e:
            log.warning("Draft law search failed: %s", e)
            return []
        return [l["code"] for l, _ in await rank(self.ranker, message, laws, law_text, 5)]

    async def generate(self, message: str, law_codes: list[str]) -> DraftDocument:
        ctx = f"Применимые нормы РК: {', '.join(law_codes)}.\n\n" if law_codes else ""
        user = f"{ctx}Запрос пользователя:\n{message}"
        doc = await self.llm.structured(DRAFT_SYSTEM, user, DraftDocument)
        if doc and doc.body.strip():
            return doc
        log.info("Structured draft unavailable - falling back to plain completion")
        body = await self.llm.complete(DRAFT_PLAIN_PROMPT, user)
        if not body or not body.strip():
            raise RuntimeError("LLM вернул пустой документ")
        return DraftDocument(title=short_title(message), body=body)

    async def _create_situation(self, req: BrainRequest) -> str | None:
        try:
            return await self.situations.create(req.graph_id, req.user_id, short_title(req.message), req.message[:500])
        except Exception as e:
            log.warning("Failed to create situation in graph %s: %s", req.graph_id, e)
            return None

    async def _link_laws(self, graph_id: str, situation_id: str, codes: list[str]) -> None:
        for code in codes:
            try:
                await self.graph.add_law(graph_id, code, "RK")
                await self.situations.link_law(graph_id, situation_id, code, "RK", "SEMANTIC")
            except Exception as e:
                log.warning("Failed to link law %s to situation %s: %s", code, situation_id, e)

    async def _create_template(self, user_id: str | None, body: str, codes: list[str], message: str) -> str | None:
        return await self.templates.create(user_id, {
            "code": f"USR-DRAFT-{uuid.uuid4().hex[:8].upper()}",
            "title": f"Черновик: {short_title(message)}",
            "description": "Создан ИИ по запросу пользователя. Можно редактировать и повторно сгенерировать документ.",
            "kind": "USER", "body": body, "suggestedLawCodes": json.dumps(codes, ensure_ascii=False),
            "sourceFormat": "MANUAL", "systemTemplate": False,
        })
