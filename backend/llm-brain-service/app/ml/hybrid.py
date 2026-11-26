from dataclasses import dataclass
from typing import Callable, Generic, TypeVar

from rank_bm25 import BM25Okapi

from app.ml import text as T
from app.ml.embeddings import Embedder
from app.ml.reranker import Reranker

D = TypeVar("D")
RRF_K = 60


@dataclass
class Ranked(Generic[D]):
    item: D
    score: float


def rrf(rankings: list[list[int]], k: int = RRF_K) -> list[int]:
    scores: dict[int, float] = {}
    for ranking in rankings:
        for rank, idx in enumerate(ranking):
            scores[idx] = scores.get(idx, 0.0) + 1.0 / (k + rank + 1)
    return sorted(scores, key=lambda i: -scores[i])


class HybridRanker:
    def __init__(self, embedder: Embedder, reranker: Reranker, candidates: int = 50, top_k: int = 8):
        self.embedder = embedder
        self.reranker = reranker
        self.candidates = candidates
        self.top_k = top_k

    async def rank(self, query: str, items: list[D], text_of: Callable[[D], str],
                   top_k: int | None = None, min_score: float = 0.0) -> list[Ranked[D]]:
        if not items:
            return []
        top_k = top_k or self.top_k
        texts = [text_of(x) or "" for x in items]
        sparse = BM25Okapi([T.stems(t) or ["_"] for t in texts]).get_scores(T.stems(query))
        sparse_rank = sorted(range(len(texts)), key=lambda i: -sparse[i])
        dense = await self.embedder.similarity(query, texts)
        dense_rank = sorted(range(len(texts)), key=lambda i: -dense[i])
        fused = rrf([dense_rank, sparse_rank])[: self.candidates]
        reranked = await self.reranker.rerank(query, [texts[i] for i in fused], top_k)
        return [Ranked(items[fused[j]], s) for j, s in reranked if s >= min_score]
