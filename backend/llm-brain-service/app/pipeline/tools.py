from pydantic import BaseModel, Field

from app.llm.tools import ToolRegistry
from app.ml.ner import NerModel
from app.pipeline.hallucination import ClaimVerifier, split_sentences
from app.pipeline.intent import IntentClassifier
from app.pipeline.profession import ProfessionClassifier
from app.pipeline.types import ContextPlan, RetrievalMode, TaskType
from app.schemas import BrainRequest


class ClassifyIntentArgs(BaseModel):
    message: str = Field(description="Текст запроса пользователя")
    hasDocument: bool = Field(False, description="Прикреплён ли документ")
    hasGraph: bool = Field(False, description="Есть ли активный граф")


class VerifyClaimsArgs(BaseModel):
    claims: list[str] = Field(default_factory=list, description="Утверждения для проверки (или пусто — тогда text)")
    text: str = Field("", description="Текст ответа; будет разбит на утверждения, если claims пуст")
    sources: list[str] = Field(description="Тексты источников (фрагменты статей/документа)")


class ExtractEntitiesArgs(BaseModel):
    text: str = Field(description="Текст для извлечения ссылок на законы/статьи, дат, сумм")


def register_tools(tools: ToolRegistry, intent: IntentClassifier, profession: ProfessionClassifier,
                   verifier: ClaimVerifier, ner: NerModel) -> None:
    async def classify_intent(a: ClassifyIntentArgs) -> dict:
        req = BrainRequest(message=a.message, document_id="_" if a.hasDocument else None,
                           graph_id="_" if a.hasGraph else None)
        plan = await intent.classify(req)
        if plan.task.requires_graph and not a.hasGraph:
            plan = ContextPlan(TaskType.GENERAL_LEGAL_ADVICE, RetrievalMode.NONE)
        profs = await profession.classify(a.message)
        return {"task": plan.task.value, "retrieval": plan.retrieval.value, "confidence": plan.confidence,
                "professions": [p.value for p in profs]}

    async def verify_claims(a: VerifyClaimsArgs) -> dict:
        claims = a.claims or [a.text[s:e] for s, e in split_sentences(a.text)]
        checks = await verifier.verify(claims, a.sources)
        return {"threshold": verifier.threshold, "results": [c.as_dict() for c in checks],
                "unsupported": sum(not c.supported for c in checks)}

    async def extract_entities(a: ExtractEntitiesArgs) -> dict:
        ents = await ner.extract(a.text or "")
        return {"entities": [{"label": e.label, "text": e.text, "start": e.start, "end": e.end,
                              "score": round(e.score, 3), **{k: v for k, v in e.attrs.items() if v is not None}}
                             for e in ents[:200]]}

    tools.register("classifyIntent", "Определить интент запроса (одна из 9 задач) и профиль юриста "
                   "(семейный/недвижимость/уголовный/корпоративный/трудовой/общий).", ClassifyIntentArgs,
                   classify_intent)
    tools.register("verifyClaims", "NLI-проверка утверждений ответа по текстам источников: entailment >= 0.8 — "
                   "подтверждено, иначе «не подтверждено».", VerifyClaimsArgs, verify_claims)
    tools.register("extractLegalEntities", "Извлечь из текста ссылки на законы и статьи, даты, суммы, сроки "
                   "(NER).", ExtractEntitiesArgs, extract_entities)
