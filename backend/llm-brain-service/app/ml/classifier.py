import asyncio
import logging
from dataclasses import dataclass
from typing import Protocol

from pydantic import BaseModel

from app.ml.embeddings import Embedder
from typing import TYPE_CHECKING
if TYPE_CHECKING:
    from app.llm.client import Completer

log = logging.getLogger(__name__)


@dataclass
class Prediction:
    label: str
    score: float
    scores: dict[str, float]


class Classifier(Protocol):
    name: str

    async def classify(self, text: str, labels: dict[str, str]) -> Prediction: ...


class FineTunedClassifier:
    name = "fine-tuned"

    def __init__(self, path: str, device: str):
        from transformers import pipeline
        self._pipe = pipeline("text-classification", model=path, top_k=None, truncation=True, max_length=256,
                              device=0 if device.startswith("cuda") else -1)
        self.multi_label = self._pipe.model.config.problem_type == "multi_label_classification"

    async def classify(self, text: str, labels: dict[str, str]) -> Prediction:
        out = (await asyncio.to_thread(self._pipe, [text]))[0]
        scores = {o["label"]: float(o["score"]) for o in out if o["label"] in labels}
        if not scores:
            return Prediction(next(iter(labels)), 0.0, {})
        if not self.multi_label:
            z = sum(scores.values()) or 1.0
            scores = {k: v / z for k, v in scores.items()}
        best = max(scores, key=scores.get)
        return Prediction(best, scores[best], scores)


class PrototypeClassifier:
    name = "prototype"

    def __init__(self, embedder: Embedder, temperature: float = 0.05):
        self.embedder = embedder
        self.t = temperature

    async def classify(self, text: str, labels: dict[str, str]) -> Prediction:
        import math
        keys = list(labels)
        sims = await self.embedder.similarity(text, [labels[k] for k in keys])
        ex = [math.exp(s / self.t) for s in sims]
        z = sum(ex) or 1.0
        scores = {k: e / z for k, e in zip(keys, ex)}
        best = max(scores, key=scores.get)
        return Prediction(best, scores[best], scores)


class _LlmLabel(BaseModel):
    label: str
    confidence: float


class LlmClassifier:
    name = "llm-zero-shot"

    def __init__(self, llm: "Completer"):
        self.llm = llm

    async def classify(self, text: str, labels: dict[str, str]) -> Prediction:
        opts = "\n".join(f"- {k}: {v}" for k, v in labels.items())
        out = await self.llm.structured(f"Классифицируй текст пользователя. Варианты:\n{opts}",
                                        text[:4000], _LlmLabel)
        if out is None or out.label not in labels:
            return Prediction(next(iter(labels)), 0.0, {})
        return Prediction(out.label, out.confidence, {out.label: out.confidence})


class CascadeClassifier:
    name = "cascade"

    def __init__(self, primary: Classifier, fallback: Classifier | None, threshold: float):
        self.primary, self.fallback, self.threshold = primary, fallback, threshold

    async def classify(self, text: str, labels: dict[str, str]) -> Prediction:
        try:
            p = await self.primary.classify(text, labels)
        except Exception as e:
            log.warning("primary classifier failed: %s", e)
            p = Prediction(next(iter(labels)), 0.0, {})
        if p.score >= self.threshold or self.fallback is None:
            return p
        try:
            f = await self.fallback.classify(text, labels)
            return f if f.score >= p.score else p
        except Exception as e:
            log.warning("fallback classifier failed: %s", e)
            return p
