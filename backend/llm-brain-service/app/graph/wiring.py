from dataclasses import dataclass

from app.container import Container
from app.draft.service import DocumentDraftService
from app.graph.actions import GraphActionService
from app.graph.deep import DeepAnalysisService
from app.graph.entity_linking import LawEntityLinker
from app.graph.link.base import SubjectLinkerRegistry
from app.graph.link.linkers import DocumentSubjectLinker, SituationSubjectLinker, VoiceEvidenceSubjectLinker
from app.graph.link.service import SubjectAwareLinkService
from app.graph.primary import KeywordSearchSource, LlmFallbackSource, PrimaryLawResolver, SubjectSeedSource
from app.pipeline.handlers.draft import DocumentDraftHandler
from app.pipeline.handlers.graph_add_laws import GraphAddLawsHandler
from app.pipeline.handlers.graph_deep_analysis import GraphDeepAnalysisHandler
from app.pipeline.handlers.graph_link import GraphLinkDocumentHandler
from app.ports import ReferenceAuditor
from app.tools.graph_tools import GraphTools, register_graph_tools


@dataclass
class GraphFeature:
    actions: GraphActionService
    deep: DeepAnalysisService
    drafts: DocumentDraftService
    linkers: SubjectLinkerRegistry
    law_linker: LawEntityLinker


def wire(c: Container, auditor: ReferenceAuditor | None = None) -> GraphFeature:
    registry = SubjectLinkerRegistry([
        VoiceEvidenceSubjectLinker(c.voice, c.graph),
        SituationSubjectLinker(c.situations),
        DocumentSubjectLinker(c.documents, c.graph, c.situations),
    ])
    actions = GraphActionService(c.graph, registry, SubjectAwareLinkService(c.graph, c.llm, c.ranker), c.ranker)
    resolver = PrimaryLawResolver([LlmFallbackSource(c.llm), KeywordSearchSource(c.graph, c.ranker),
                                   SubjectSeedSource()], c.graph)
    deep = DeepAnalysisService(c.graph, c.situations, resolver, c.ranker, c.embedder, c.nli, c.settings.deep,
                               auditor=auditor)
    drafts = DocumentDraftService(c.llm, c.graph, c.situations, c.templates, c.ranker)

    register_graph_tools(c.tools, GraphTools(
        laws=c.laws, documents=c.documents, graph=c.graph, situations=c.situations, embedder=c.embedder,
        reranker=c.reranker, ranker=c.ranker, ner=c.ner, nli=c.nli, actions=actions,
        top_k=c.settings.ranking.top_k))
    for h in (GraphLinkDocumentHandler(actions), GraphAddLawsHandler(actions),
              GraphDeepAnalysisHandler(deep, registry), DocumentDraftHandler(drafts)):
        c.handlers.register(h)
    law_linker = LawEntityLinker(c.graph, c.embedder, c.reranker, c.settings.thresholds.law_link)
    return GraphFeature(actions, deep, drafts, registry, law_linker)
