import logging
from dataclasses import dataclass

import httpx

from app.clients.base import ServiceClient
from app.clients.ports import DocumentPort, GraphPort, LawPort, SituationPort, TemplatePort, VoicePort
from app.clients.services import (DocumentClient, GraphClient, LawClient, SituationClient, TemplateClient,
                                  VoiceClient)
from app.config import Settings
from app.llm.client import LLMClient
from app.llm.messages import MessageBuilder
from app.llm.tools import ToolRegistry
from app.ml.classifier import Classifier
from app.ml.embeddings import Embedder
from app.ml.factory import build_models
from app.ml.hybrid import HybridRanker
from app.ml.ner import NerModel
from app.ml.nli import NliModel
from app.ml.reranker import Reranker
from app.pipeline.handlers.base import HandlerRegistry

log = logging.getLogger(__name__)


@dataclass
class Container:
    settings: Settings
    http: httpx.AsyncClient
    documents: DocumentPort
    graph: GraphPort
    laws: LawPort
    situations: SituationPort
    templates: TemplatePort
    voice: VoicePort
    tools: ToolRegistry
    llm: LLMClient
    embedder: Embedder
    reranker: Reranker
    ranker: HybridRanker
    nli: NliModel
    ner: NerModel
    classifier: Classifier
    intent_classifier: Classifier
    profession_classifier: Classifier
    handlers: HandlerRegistry

    async def aclose(self) -> None:
        await self.http.aclose()


def build_container(s: Settings, messages: MessageBuilder | None = None, llm: LLMClient | None = None,
                    http: httpx.AsyncClient | None = None) -> Container:
    http = http or httpx.AsyncClient(timeout=s.http_timeout_s)
    svc = lambda name, url=None: ServiceClient(name, http, url)  # noqa: E731
    tools = ToolRegistry()
    llm = llm or LLMClient.from_settings(s, tools, messages)

    m = build_models(s, llm)

    return Container(
        settings=s, http=http,
        documents=DocumentClient(svc("document-service")),
        graph=GraphClient(svc("graphs-service", s.graph_service_url)),
        laws=LawClient(svc("law-service")),
        situations=SituationClient(svc("situation-service")),
        templates=TemplateClient(svc("template-service")),
        voice=VoiceClient(svc("voice-service")),
        tools=tools, llm=llm, embedder=m.embedder, reranker=m.reranker,
        ranker=HybridRanker(m.embedder, m.reranker, s.rerank_candidates, s.rerank_top_k),
        nli=m.nli, ner=m.ner, classifier=m.classifier,
        intent_classifier=m.intent_classifier, profession_classifier=m.profession_classifier,
        handlers=HandlerRegistry(),
    )

