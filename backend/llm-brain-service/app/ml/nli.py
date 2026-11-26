import asyncio
from dataclasses import dataclass
from typing import Protocol

from pydantic import BaseModel
from typing import TYPE_CHECKING
if TYPE_CHECKING:
    from app.llm.client import Completer


@dataclass
class NliResult:
    entailment: float
    neutral: float
    contradiction: float

    @property
    def label(self) -> str:
        return max(("entailment", self.entailment), ("neutral", self.neutral),
                   ("contradiction", self.contradiction), key=lambda x: x[1])[0]


class NliModel(Protocol):
    name: str

    async def predict(self, pairs: list[tuple[str, str]]) -> list[NliResult]: ...


class TransformersNli:
    name = "mdeberta-v3-xnli"

    def __init__(self, model: str, device: str):
        from transformers import pipeline
        self._pipe = pipeline("text-classification", model=model, device=0 if device.startswith("cuda") else -1,
                              top_k=None, truncation=True, max_length=512)

    async def predict(self, pairs: list[tuple[str, str]]) -> list[NliResult]:
        if not pairs:
            return []
        inputs = [{"text": p, "text_pair": h} for p, h in pairs]
        outs = await asyncio.to_thread(self._pipe, inputs, batch_size=8)
        res = []
        for out in outs:
            d = {o["label"].lower(): float(o["score"]) for o in out}
            res.append(NliResult(d.get("entailment", 0.0), d.get("neutral", 0.0), d.get("contradiction", 0.0)))
        return res


class _Verdict(BaseModel):
    index: int
    label: str
    confidence: float


class _Verdicts(BaseModel):
    items: list[_Verdict]


class LlmNli:
    name = "llm-nli"
    SYSTEM = ("Ты — модуль NLI (natural language inference) для юридических текстов. Для каждой пары "
              "ИСТОЧНИК/УТВЕРЖДЕНИЕ определи: entailment (источник подтверждает), contradiction "
              "(противоречит), neutral (не следует). confidence 0..1.")

    def __init__(self, llm: "Completer"):
        self.llm = llm

    async def predict(self, pairs: list[tuple[str, str]]) -> list[NliResult]:
        if not pairs:
            return []
        body = "\n\n".join(f"#{i}\nИСТОЧНИК: {p[:1500]}\nУТВЕРЖДЕНИЕ: {h[:600]}" for i, (p, h) in enumerate(pairs))
        out = await self.llm.structured(self.SYSTEM, body, _Verdicts)
        res = [NliResult(0.0, 1.0, 0.0) for _ in pairs]
        for v in out.items if out else []:
            if 0 <= v.index < len(res):
                c = max(0.0, min(1.0, v.confidence))
                rest = (1 - c) / 2
                res[v.index] = NliResult(
                    c if v.label == "entailment" else rest,
                    c if v.label == "neutral" else rest,
                    c if v.label == "contradiction" else rest)
        return res
