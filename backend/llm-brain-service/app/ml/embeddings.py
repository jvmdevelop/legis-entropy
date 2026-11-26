import asyncio
import logging
from typing import Protocol

from app.ml import text as T

log = logging.getLogger(__name__)


class Embedder(Protocol):
    name: str

    async def embed(self, texts: list[str]) -> list[list[float]]: ...

    async def similarity(self, a: str, bs: list[str]) -> list[float]: ...


class _SimilarityMixin:
    async def similarity(self, a: str, bs: list[str]) -> list[float]:
        if not bs:
            return []
        vecs = await self.embed([a, *bs])
        return [T.cosine(vecs[0], v) for v in vecs[1:]]


class BgeM3Embedder(_SimilarityMixin):
    name = "bge-m3"

    def __init__(self, model: str, device: str, max_tokens: int = 512, batch_size: int = 8):
        from sentence_transformers import SentenceTransformer
        self._model = SentenceTransformer(model, device=device)
        self._model.max_seq_length = min(self._model.max_seq_length or max_tokens, max_tokens)
        self._batch = batch_size

    async def embed(self, texts: list[str]) -> list[list[float]]:
        vecs = await asyncio.to_thread(self._model.encode, texts, normalize_embeddings=True, batch_size=self._batch)
        return [v.tolist() for v in vecs]


class TrigramEmbedder(_SimilarityMixin):
    name = "trigram"
    DIM = 2048

    async def embed(self, texts: list[str]) -> list[list[float]]:
        out = []
        for t in texts:
            v = [0.0] * self.DIM
            for g, c in T.trigram_vector(t).items():
                v[hash(g) % self.DIM] += c
            out.append(v)
        return out

    async def similarity(self, a: str, bs: list[str]) -> list[float]:
        va = T.trigram_vector(a)
        return [T.cosine_counter(va, T.trigram_vector(b)) for b in bs]
