from dataclasses import dataclass, field

from fastapi import APIRouter

from app.api import brain as brain_api
from app.container import Container
from app.pipeline.brain import BrainPipeline
from app.pipeline.context import DocumentContextFormatter
from app.pipeline.handlers.default import DefaultBrainHandler
from app.pipeline.hallucination import AnswerPostProcessor, ArticleIndex, ClaimVerifier, HallucinationDetector
from app.pipeline.intent import IntentClassifier
from app.pipeline.profession import ProfessionClassifier
from app.pipeline.retrieval import (HybridLegalRetrievalStrategy, LawRetrievalStrategy, NoRetrievalStrategy,
                                    QueryRewriter, RetrievalRouter, UserDocumentRetrievalStrategy)
from app.pipeline.tools import register_tools
from app.pipeline.handlers.edit import DocumentEditHandler
from app.ports import EditSuggester, LawGraphWriter, LawNameLinker, ReferenceReports
from app.prompt.builder import BrainPromptBuilder, SubjectTextLoader


@dataclass
class PipelineFeature:
    pipeline: BrainPipeline
    routers: list[APIRouter] = field(default_factory=list)


def wire(c: Container, reports: ReferenceReports | None = None,
         graph_writer: LawGraphWriter | None = None, law_linker: LawNameLinker | None = None,
         edits: EditSuggester | None = None) -> PipelineFeature:
    t, top_k = c.settings.thresholds, c.settings.ranking.top_k
    formatter = DocumentContextFormatter()
    doc = UserDocumentRetrievalStrategy(c.documents, formatter, c.ranker, reports, top_k)
    law = LawRetrievalStrategy(c.laws, c.graph, formatter, QueryRewriter(c.llm), c.ranker, c.embedder, top_k)
    router = RetrievalRouter([doc, law, HybridLegalRetrievalStrategy(doc, law), NoRetrievalStrategy()])

    detector = HallucinationDetector(ArticleIndex(c.laws, reports))
    claims = ClaimVerifier(c.nli, c.ner, c.embedder, t.nli_entailment)
    prompts = BrainPromptBuilder(SubjectTextLoader(c.documents, c.situations, c.voice))
    intent = IntentClassifier(c.intent_classifier, t.intent_confidence)
    profession = ProfessionClassifier(c.profession_classifier, t.profession)

    c.handlers.set_default(DefaultBrainHandler(router, prompts, c.llm, AnswerPostProcessor(detector, claims),
                                               detector, graph_actions=graph_writer, ner=c.ner,
                                               linker=law_linker, edits=edits, documents=c.documents))
    register_tools(c.tools, intent, profession, claims, c.ner)
    edit_handler = DocumentEditHandler(edits, c.documents) if hasattr(edits, "apply") else None
    pipeline = BrainPipeline(intent, profession, c.handlers, edit_handler)
    return PipelineFeature(pipeline, routers=[brain_api.build_router(pipeline)])
