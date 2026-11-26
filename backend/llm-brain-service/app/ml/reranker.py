import asyncio
import math
from typing import Protocol

from rank_bm25 import BM25Okapi

from app.ml import text as T


class Reranker(Protocol):
    name: str

    async def rerank(self, query: str, docs: list[str], top_k: int) -> list[tuple[int, float]]: ...


class CrossEncoderReranker:
    name = "bge-reranker-v2-m3"

    def __init__(self, model: str, device: str):
        from sentence_transformers import CrossEncoder
        self._model = CrossEncoder(model, device=device, max_length=512)

    async def rerank(self, query: str, docs: list[str], top_k: int) -> list[tuple[int, float]]:
        if not docs:
            return []
        scores = await asyncio.to_thread(self._model.predict, [(query, d) for d in docs])
        probs = [1 / (1 + math.exp(-float(s))) for s in scores]
        return sorted(enumerate(probs), key=lambda x: -x[1])[:top_k]


class Bm25Reranker:
    name = "bm25"

    async def rerank(self, query: str, docs: list[str], top_k: int) -> list[tuple[int, float]]:
        if not docs:
            return []
        corpus = [T.stems(d) or ["_"] for d in docs]
        scores = [float(x) for x in BM25Okapi(corpus).get_scores(T.stems(query))]
        lo, hi = min(scores), max(scores)
        norm = [(x - lo) / (hi - lo) if hi > lo else 0.5 for x in scores]
        return sorted(enumerate(norm), key=lambda x: -x[1])[:top_k]
