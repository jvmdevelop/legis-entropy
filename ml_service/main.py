import time
from contextlib import asynccontextmanager

import torch
from fastapi import FastAPI
from sentence_transformers import SentenceTransformer

from analyzer import DocumentAnalyzer
from models import (
    AnalyzeRequest, AnalyzeResponse,
    SearchRequest, SearchResponse, SearchResult,
    CompareRequest, CompareResponse,
)

MODEL_NAME = "cointegrated/rubert-tiny2"

_analyzer: DocumentAnalyzer | None = None
_device: str = "cpu"


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _analyzer, _device

    _device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Loading {MODEL_NAME} on {_device}...")

    model = SentenceTransformer(MODEL_NAME, device=_device)
    _analyzer = DocumentAnalyzer(model, _device)

    print(f"Model ready on {_device}")
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
    return AnalyzeResponse(issues=issues, model_name=MODEL_NAME, device=_device)


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


@app.get("/health")
async def health():
    return {"status": "ok", "device": _device, "model": MODEL_NAME}
