import asyncio
import logging

from app.analysis.clauses import Clause  # noqa: F401
from app.analysis.risk_text import model_input
from app.analysis.contract_risk import RiskClassifier
from app.schemas import ClauseRiskResult

log = logging.getLogger(__name__)
LEVELS = ("VIOLATION", "WARNING", "COMPLIANT")


class FineTunedRiskClassifier:
    name = "fine-tuned-risk"

    def __init__(self, path: str, device: str, explainer: RiskClassifier | None, confidence: float = 0.6):
        from transformers import pipeline
        self._pipe = pipeline("text-classification", model=path, top_k=None, truncation=True, max_length=256,
                              device=0 if device.startswith("cuda") else -1)
        self.explainer = explainer
        self.confidence = confidence

    async def _predict(self, batch: list[Clause], contract_type: str) -> list[tuple[str, float]]:
        outs = await asyncio.to_thread(self._pipe, [model_input(contract_type, c.text) for c in batch])
        res = []
        for out in outs:
            best = max(out, key=lambda o: o["score"])
            res.append((best["label"] if best["label"] in LEVELS else "COMPLIANT", float(best["score"])))
        return res

    async def classify(self, batch, contract_type, norms_ctx):
        preds = await self._predict(batch, contract_type)
        results = {c.id: ClauseRiskResult(clause_id=c.id, clause_text=c.text, risk_level=lvl)
                   for c, (lvl, _) in zip(batch, preds)}
        to_explain = [c for c, (lvl, p) in zip(batch, preds) if lvl != "COMPLIANT" or p < self.confidence]
        if to_explain and self.explainer is not None:
            explained = await self.explainer.classify(to_explain, contract_type, norms_ctx) or []
            for r in explained:
                base = results.get(r.clause_id)
                if base is None:
                    continue
                pred_level = base.risk_level
                p = dict(zip([c.id for c in batch], [x[1] for x in preds]))[r.clause_id]
                level = r.risk_level if p < self.confidence else pred_level
                results[r.clause_id] = base.model_copy(update={
                    "risk_level": level,
                    "law_citation": r.law_citation if level != "COMPLIANT" else None,
                    "reason": r.reason if level != "COMPLIANT" else None})
        log.info("risk model: %d clauses, %d sent to LLM", len(batch), len(to_explain))
        return [results[c.id] for c in batch]
