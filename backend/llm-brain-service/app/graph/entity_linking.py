import logging
import time
from dataclasses import dataclass

from app.clients.ports import GraphPort
from app.ml import text as T
from app.ml.embeddings import Embedder
from app.ml.reranker import Reranker

log = logging.getLogger(__name__)

MAX_CANDIDATES = 10
_QUOTED_PREFIXES = ("закон", "кодекс")


@dataclass(frozen=True)
class LinkedLaw:
    code: str
    title: str
    score: float


def _norm(name: str) -> str:
    return T.normalize(name).replace("«", "\"").replace("»", "\"").strip(" .,;:")


def _title(law: dict) -> str:
    return law.get("title") or ""


class LawEntityLinker:
    def __init__(self, graph: GraphPort, embedder: Embedder, reranker: Reranker, threshold: float = 0.75,
                 margin: float = 0.05, ttl_s: float = 6 * 3600, negative_ttl_s: float = 600,
                 clock=time.monotonic):
        self.graph, self.embedder, self.reranker = graph, embedder, reranker
        self.threshold, self.margin = threshold, margin
        self.ttl_s, self.negative_ttl_s = ttl_s, negative_ttl_s
        self._clock = clock
        self._cache: dict[tuple[str, str], tuple[LinkedLaw | None, float]] = {}

    async def link(self, name: str, country: str = "RK") -> LinkedLaw | None:
        key = (_norm(name), country)
        if not key[0]:
            return None
        hit = self._cache.get(key)
        if hit and hit[1] > self._clock():
            return hit[0]
        result = await self._resolve(name, country)
        self._cache[key] = (result, self._clock() + (self.ttl_s if result else self.negative_ttl_s))
        return result

    async def _resolve(self, name: str, country: str) -> LinkedLaw | None:
        try:
            candidates = [c for c in (await self.graph.search_laws(name, country) or []) if c.get("code")]
        except Exception as e:
            log.debug("law linking search failed for %r: %s", name, e)
            return None
        candidates = candidates[:MAX_CANDIDATES]
        if not candidates:
            return None
        scores = await self._score(name, candidates)
        order = sorted(range(len(candidates)), key=lambda i: -scores[i])
        best = order[0]
        second = scores[order[1]] if len(order) > 1 else 0.0
        if scores[best] < self.threshold or scores[best] - second < self.margin:
            log.info("law link rejected %r: best=%.2f second=%.2f", name, scores[best], second)
            return None
        law = candidates[best]
        return LinkedLaw(law["code"], _title(law), round(scores[best], 3))

    async def _score(self, name: str, candidates: list[dict]) -> list[float]:
        titles = [_title(c) for c in candidates]
        target = _norm(name)
        tq = self._quoted(target)
        exact = [1.0 if _norm(t) == target or (tq is not None and tq == self._quoted(_norm(t))) else 0.0
                 for t in titles]
        cos = [max(0.0, s) for s in await self.embedder.similarity(name, titles)]
        if getattr(self.reranker, "name", "") == "bm25":
            dense = cos
        else:
            rr = dict(await self.reranker.rerank(name, titles, len(titles)))
            dense = [(c + rr.get(i, 0.0)) / 2 for i, c in enumerate(cos)]
        return [max(e, d) for e, d in zip(exact, dense)]

    @staticmethod
    def _quoted(s: str) -> str | None:
        if '"' not in s or not s.startswith(_QUOTED_PREFIXES):
            return None
        parts = s.split('"')
        return parts[1].strip() if len(parts) > 2 and parts[1].strip() else None
