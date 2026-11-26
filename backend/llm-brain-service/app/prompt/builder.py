import logging
from dataclasses import dataclass

from app.pipeline.context import DocumentContext
from app.pipeline.profession import instruction_for
from app.pipeline.types import ContextPlan, Profession, RetrievalMode
from app.prompt import instructions as I
from app.schemas import BrainRequest
from app.clients.ports import DocumentPort
from app.clients.ports import SituationPort
from app.clients.ports import VoicePort

log = logging.getLogger(__name__)

MAX_SUBJECT_CHARS = 4000
LAW_REMINDER_TERMS = ("закон", "статья", "кодекс", "норм", "требование", "срок", "обязанность", "право")


@dataclass
class Subject:
    display_name: str
    accusative: str
    label: str | None
    text: str


class SubjectTextLoader:
    def __init__(self, documents: DocumentPort | None, situations: SituationPort | None,
                 voice: VoicePort | None):
        self.documents, self.situations, self.voice = documents, situations, voice

    async def load(self, req: BrainRequest) -> Subject | None:
        try:
            if req.has_voice:
                v = await self.voice.get(req.voice_id) or {}
                text = v.get("transcript") or v.get("analysisJson") or ""
                return Subject("Голосовое сообщение", "голосовое сообщение", v.get("fileName"), str(text))
            if req.has_situation and req.has_graph:
                s = await self.situations.get(req.graph_id, req.situation_id)
                if not s:
                    return None
                return Subject("Ситуация", "ситуация", s.get("title"), str(s.get("plainText") or s.get("body") or ""))
            if req.has_document:
                text = await self._document_text(req)
                return Subject("Документ", "документ", None, text)
        except Exception as e:
            log.debug("Could not load subject text: %s", e)
        return None

    async def _document_text(self, req: BrainRequest) -> str:
        try:
            text = await self.documents.text(req.document_id, req.user_id)
            if text and text.strip():
                return text
        except Exception as e:
            log.debug("getDocumentText failed for %s: %s", req.document_id, e)
        try:
            chunks = await self.documents.search("договор", req.user_id, req.document_id) or []
            return " ".join(str(c.get("text") or "") for c in chunks[:5])
        except Exception:
            return ""


class BrainPromptBuilder:
    def __init__(self, subjects: SubjectTextLoader | None = None):
        self.subjects = subjects

    async def build_user_prompt(self, req: BrainRequest, ctx: DocumentContext | None, plan: ContextPlan,
                                professions: tuple[Profession, ...] | None = None) -> str:
        professions = professions or plan.professions
        has_context = ctx is not None and not ctx.unavailable and bool((ctx.text or "").strip())
        task_instruction = I.DOCUMENT_ANALYSIS_INSTRUCTION if plan.task.is_document_task and has_context \
            else I.GENERAL_ADVICE_INSTRUCTION
        profession = self.profession_block(professions)
        tools = self.tool_reminder(req.message)
        graph = self.graph_block(req)
        subject = await self.subject_block(req)
        if has_context:
            aware = self.context_aware_instruction(has_context, plan)
            return (f"Контекст документа:\n{ctx.text}\n\n{subject}Вопрос пользователя:\n{req.message}\n\n"
                    f"{graph}{profession}{tools}{aware}Инструкция:\n{task_instruction}\n")
        return (f"{subject}Вопрос пользователя:\n{req.message}\n\n"
                f"{graph}{profession}{tools}Инструкция:\n{task_instruction}\n")

    async def subject_block(self, req: BrainRequest) -> str:
        if not req.has_graph or self.subjects is None:
            return ""
        s = await self.subjects.load(req)
        if s is None or not s.text or not s.text.strip():
            return ""
        text = s.text if len(s.text) <= MAX_SUBJECT_CHARS else s.text[:MAX_SUBJECT_CHARS] + "\n…(обрезано)"
        header = s.display_name if not s.label or not str(s.label).strip() else f"{s.display_name} «{s.label}»"
        return f"Активный {s.accusative} (используй для ответа):\n— {header} —\n{text}\n— конец —\n\n"

    @staticmethod
    def graph_block(req: BrainRequest) -> str:
        if not req.has_graph:
            return ""
        sb = ["АКТИВНЫЙ КОНТЕКСТ (подставляй ЭТИ значения как параметры инструментов, не спрашивай их у "
              "пользователя):\n", f"- graphId = {req.graph_id}\n"]
        if req.has_document:
            sb.append(f"- documentId = {req.document_id}\n")
        if req.has_situation:
            sb.append(f"- situationId = {req.situation_id}\n")
        if req.has_voice:
            sb.append(f"- voiceId = {req.voice_id}\n")
        if req.user_id and req.user_id.strip():
            sb.append(f"- userId = {req.user_id}\n")
        sb.append("\n")
        sb.append("ОБЯЗАТЕЛЬНЫЕ ДЕЙСТВИЯ — при этих формулировках СРАЗУ вызови инструмент, НЕ описывай план:\n")
        if req.has_document:
            sb.append("- «свяжи мой документ с законами / нормами / применимыми статьями», «привяжи документ к "
                      "законам», «найди применимые к документу нормы» → ВЫЗОВИ linkDocumentToLawsInGraph(graphId, "
                      "documentId, userId). Документ уже загружен — его ID выше. НЕ проси пользователя "
                      "«предоставить документ».\n")
        elif req.has_situation or req.has_voice:
            sb.append("- «свяжи ситуацию с законами», «свяжи ГС с законами», «найди применимые нормы» — backend "
                      "сам подберёт законы по тексту субъекта (детерминированно). Не описывай план, дождись "
                      "результата.\n")
        else:
            sb.append("- если пользователь просит связать документ/ситуацию, но ни documentId/situationId/voiceId "
                      "выше НЕ указаны — ответь ОДНОЙ строкой: «Объект не привязан к графу — выберите ноду или "
                      "загрузите файл». Никаких многошаговых планов.\n")
        sb.append("- «добавь закон X в граф», «помести закон в граф» → ВЫЗОВИ addLawToUserGraph(graphId, code, "
                  "country).\n")
        sb.append("- «найди и добавь законы про X», «подбери нормы по теме X в граф» → ВЫЗОВИ "
                  "findAndAddRelatedLawsToGraph(graphId, query, country, limit).\n")
        sb.append("После вызова инструмента кратко перечисли, что было добавлено/связано. НЕ выводи JSON "
                  "параметров и НЕ пиши «что нужно сделать» — это уже сделано тобой.\n\n")
        return "".join(sb)

    @staticmethod
    def tool_reminder(message: str | None) -> str:
        m = (message or "").lower()
        if any(t in m for t in LAW_REMINDER_TERMS):
            return ("⚠️ Вопрос касается конкретных норм/законов. ОБЯЗАТЕЛЬНО используй searchLaws() для поиска "
                    "точного текста.\n\n")
        return ""

    @staticmethod
    def profession_block(professions: tuple[Profession, ...]) -> str:
        return "Профиль специализации:\n" + instruction_for(professions) + "\n"

    @staticmethod
    def context_aware_instruction(has_context: bool, plan: ContextPlan) -> str:
        if has_context and plan.retrieval in (RetrievalMode.LAW, RetrievalMode.HYBRID_LEGAL):
            return ("Выше уже предоставлен контекст нормативной базы. Используй searchLaws() ТОЛЬКО если нужна "
                    "информация, которой нет в предоставленном контексте — другие статьи или связанные законы.\n\n")
        return ""
