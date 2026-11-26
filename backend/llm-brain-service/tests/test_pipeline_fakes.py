from app.llm.tools import ToolRegistry
from app.ml.classifier import Prediction
from app.ml.embeddings import TrigramEmbedder
from app.ml.hybrid import HybridRanker
from app.ml.ner import RegexNer
from app.ml.nli import NliResult
from app.ml.reranker import Bm25Reranker
from app.pipeline.brain import BrainPipeline
from app.pipeline.context import DocumentContextFormatter
from app.pipeline.handlers.base import HandlerRegistry
from app.pipeline.handlers.default import DefaultBrainHandler
from app.pipeline.hallucination import AnswerPostProcessor, ArticleIndex, ClaimVerifier, HallucinationDetector
from app.pipeline.intent import IntentClassifier
from app.pipeline.profession import ProfessionClassifier
from app.pipeline.retrieval import (HybridLegalRetrievalStrategy, LawRetrievalStrategy, NoRetrievalStrategy,
                                    QueryRewriter, RetrievalRouter, UserDocumentRetrievalStrategy)
from app.prompt.builder import BrainPromptBuilder, SubjectTextLoader
from app.schemas import BrainResponse


class FakeLLM:
    def __init__(self, answer="Ответ.", chunks=None, fail_stream=False, structured_result=None):
        self.answer_text = answer
        self.chunks = chunks if chunks is not None else [answer]
        self.fail_stream = fail_stream
        self.structured_result = structured_result
        self.prompts: list[str] = []
        self.tools = ToolRegistry()

    async def answer(self, prompt, history=None, use_tools=True, tools=None):
        self.prompts.append(prompt)
        return BrainResponse(content=self.answer_text, tokens=7)

    async def stream(self, prompt, history=None, use_tools=True, tools=None):
        self.prompts.append(prompt)
        for c in self.chunks:
            yield c
        if self.fail_stream:
            raise RuntimeError("boom")

    async def structured(self, system, user, schema):
        return self.structured_result

    async def complete(self, system, user):
        return ""


class FakeLaws:
    def __init__(self, hits_by_query=None, default_hits=None, titles=None):
        self.hits_by_query = hits_by_query or {}
        self.default_hits = default_hits or []
        self.titles = titles or {}
        self.queries: list[str] = []

    async def search(self, query, country=None):
        self.queries.append(query)
        return self.hits_by_query.get(query, self.default_hits)

    async def article_titles(self, code, country):
        return [{"articleNumber": n, "articleTitle": ""} for n in self.titles.get(code, [])]


class FakeDocs:
    def __init__(self, text=None, chunks=None, fail_search=False):
        self._text, self._chunks, self.fail_search = text, chunks or [], fail_search

    async def text(self, document_id, user_id):
        return self._text

    async def search(self, query, user_id, document_id=None):
        if self.fail_search:
            raise RuntimeError("down")
        return self._chunks


class FakeGraph:
    def __init__(self, related=None):
        self._related = related or {}
        self.calls: list[str] = []

    async def related(self, code, country):
        self.calls.append(code)
        return self._related.get(code, [])


class FakeGraphActions:
    def __init__(self):
        self.calls = []

    async def add_law_codes(self, graph_id, codes, country):
        self.calls.append((graph_id, list(codes), country))
        return list(codes)


class FakeClassifier:
    def __init__(self, label=None, score=0.0):
        self.label, self.score, self.calls = label, score, 0

    async def classify(self, text, labels):
        self.calls += 1
        label = self.label if self.label in labels else next(iter(labels))
        return Prediction(label, self.score, {label: self.score})


class SubstringNli:
    name = "fake-nli"

    def __init__(self, key: str):
        self.key = key

    async def predict(self, pairs):
        return [NliResult(0.95, 0.03, 0.02) if self.key in p and self.key in h else NliResult(0.1, 0.8, 0.1)
                for p, h in pairs]


def law_hit(text, code="414-V", article=None, doc="law-1", idx=0):
    md = {"lawCode": code}
    if article is not None:
        md["articleNumber"] = article
    return {"text": text, "documentId": doc, "chunkIndex": idx, "score": 0.9, "metadata": md}


def build(llm=None, laws=None, docs=None, graph=None, actions=None, nli=None, classifier=None):
    llm = llm or FakeLLM()
    laws = laws or FakeLaws()
    docs = docs or FakeDocs()
    graph = graph or FakeGraph()
    emb = TrigramEmbedder()
    ranker = HybridRanker(emb, Bm25Reranker(), 50, 8)
    fmt = DocumentContextFormatter()
    doc = UserDocumentRetrievalStrategy(docs, fmt, ranker)
    law = LawRetrievalStrategy(laws, graph, fmt, QueryRewriter(None), ranker, emb)
    router = RetrievalRouter([doc, law, HybridLegalRetrievalStrategy(doc, law), NoRetrievalStrategy()])
    detector = HallucinationDetector(ArticleIndex(laws))
    post = AnswerPostProcessor(detector, ClaimVerifier(nli, RegexNer(), emb, 0.8) if nli else None)
    handler = DefaultBrainHandler(router, BrainPromptBuilder(SubjectTextLoader(docs, None, None)), llm, post,
                                  detector, graph_actions=actions, ner=RegexNer())
    reg = HandlerRegistry()
    reg.set_default(handler)
    pipeline = BrainPipeline(IntentClassifier(classifier), ProfessionClassifier(classifier), reg)
    return pipeline, handler
