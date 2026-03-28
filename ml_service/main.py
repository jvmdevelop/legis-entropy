import time
from contextlib import asynccontextmanager

import torch
from fastapi import FastAPI
from sentence_transformers import SentenceTransformer

from analyzer import DocumentAnalyzer
from config import BERT_MODEL_NAME
from llm import LLMService
from models import (
    AnalyzeRequest, AnalyzeResponse,
    CompareRequest, CompareResponse,
    CorpusReviewRequest, CorpusReviewResponse,
    ReviewRequest, ReviewResponse,
    SearchRequest, SearchResponse,
)

_analyzer: DocumentAnalyzer | None = None
_llm: LLMService | None = None
_device: str = "cpu"


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _analyzer, _llm, _device

    _device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Loading {BERT_MODEL_NAME} on {_device}…")

    bert = SentenceTransformer(BERT_MODEL_NAME, device=_device)
    _llm = LLMService()
    _analyzer = DocumentAnalyzer(bert, _device, _llm)

    print("BERT model ready. Starting LLM load in background…")
    _llm.load_in_background()

    yield


app = FastAPI(title="legis-entropy ML service", lifespan=lifespan)


def _get_analyzer() -> DocumentAnalyzer:
    assert _analyzer is not None, "Analyzer not initialised"
    return _analyzer


@app.post("/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    t0 = time.monotonic()
    issues = _get_analyzer().analyze(request.documents)
    print(f"analyze: {len(request.documents)} docs → {len(issues)} issues ({round((time.monotonic()-t0)*1000)}ms)")
    return AnalyzeResponse(issues=issues, model_name=BERT_MODEL_NAME, device=_device)


@app.post("/search", response_model=SearchResponse)
async def search(request: SearchRequest) -> SearchResponse:
    t0 = time.monotonic()
    results = _get_analyzer().search(request.query, request.documents, request.top_k)
    print(f"search: '{request.query}' → {len(results)} results ({round((time.monotonic()-t0)*1000)}ms)")
    return SearchResponse(results=results)


@app.post("/compare", response_model=CompareResponse)
async def compare(request: CompareRequest) -> CompareResponse:
    t0 = time.monotonic()
    result = _get_analyzer().compare(request.doc_a, request.doc_b)
    print(f"compare: {request.doc_a.id} vs {request.doc_b.id} → {result.similarity:.2f} ({round((time.monotonic()-t0)*1000)}ms)")
    return result


@app.post("/review", response_model=ReviewResponse)
async def review(request: ReviewRequest) -> ReviewResponse:
    t0 = time.monotonic()
    text, ready = _get_analyzer().review_compare(request)
    print(f"review: {round((time.monotonic()-t0)*1000)}ms, ready={ready}")
    return ReviewResponse(review=text, model_ready=ready)


@app.post("/corpus-review", response_model=CorpusReviewResponse)
async def corpus_review(request: CorpusReviewRequest) -> CorpusReviewResponse:
    t0 = time.monotonic()
    text, ready = _get_analyzer().review_corpus(
        total=request.total_docs,
        active=request.active_count,
        outdated=request.outdated_count,
        with_issues=request.with_issues,
        issue_types=request.issue_types,
        most_problematic=request.most_problematic,
    )
    print(f"corpus-review: {round((time.monotonic()-t0)*1000)}ms, ready={ready}")
    return CorpusReviewResponse(review=text, model_ready=ready)


@app.get("/health")
async def health():
    return {
        "status": "ok",
        "device": _device,
        "model": BERT_MODEL_NAME,
        "llm_ready": _llm.ready if _llm else False,
    }
