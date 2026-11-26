import logging
from typing import Protocol

from pydantic import BaseModel, RootModel, field_validator

from app.analysis.clauses import Clause, ClauseSegmenter
from app.analysis.contract_types import ContractTypeService
from app.analysis.required_clauses import RequiredClauseChecker
from app.schemas import ClauseRiskResult, RiskScanResponse
from app.llm.client import Completer
from app.clients.ports import LawPort

log = logging.getLogger(__name__)

BATCH_SIZE = 15
MAX_CONTEXT_ARTICLES = 20

SEARCH_QUERIES: dict[str, list[str]] = {
    "трудовой": ["трудовой договор условия найма зарплата испытательный срок",
                 "права и обязанности работника работодателя увольнение"],
    "аренды": ["договор аренды имущества арендная плата права арендатора",
               "обязанности арендодателя состояние имущества расторжение аренды"],
    "поставки": ["договор поставки товара покупатель продавец цена оплата",
                 "условия поставки качество товара ответственность сторон"],
    "услуг": ["договор возмездного оказания услуг исполнитель заказчик оплата",
              "приемка услуг ответственность за качество услуг"],
    "подряда": ["договор подряда подрядчик заказчик выполнение работ смета",
                "приемка работ гарантии качества подрядные отношения"],
}
DEFAULT_QUERIES = ["договор права и обязанности сторон ответственность расторжение",
                   "гражданский кодекс договорные отношения обязательства"]

PROMPT = """Ты — эксперт по законодательству Республики Казахстан.
Проанализируй пункты договора {contract_type} на соответствие нормам РК.

{norms}
ПУНКТЫ ДОГОВОРА (формат [номер] текст):
{clauses}

Верни ТОЛЬКО JSON-массив (без markdown-обёртки и без пояснений):
[{{"clauseId":"1.1","riskLevel":"COMPLIANT","lawCitation":null,"reason":"..."}},...]

Правила классификации:
- COMPLIANT — соответствует нормам или не регулируется законом
- WARNING — потенциально невыгодный для клиента пункт, но закон не нарушен
- VIOLATION — прямо нарушает обязательную норму закона

Для VIOLATION и WARNING обязательно укажи lawCitation ("ст. X Закона") и reason.
Для COMPLIANT оставь lawCitation: null, reason может быть null.
"""
SYSTEM = "Ты — эксперт по законодательству Республики Казахстан."


def normalize_level(raw: str | None) -> str:
    r = (raw or "").upper()
    if r in ("VIOLATION", "НАРУШЕНИЕ"):
        return "VIOLATION"
    if r in ("WARNING", "ПРЕДУПРЕЖДЕНИЕ", "WARN"):
        return "WARNING"
    return "COMPLIANT"


def _clean(v) -> str | None:
    if v is None:
        return None
    s = str(v).strip()
    return None if not s or s == "null" else s


class _RiskItem(BaseModel):
    clauseId: str | None = None
    riskLevel: str | None = None
    lawCitation: str | None = None
    reason: str | None = None

    @field_validator("*", mode="before")
    @classmethod
    def _str(cls, v):
        return _clean(v)


class _RiskItems(RootModel[list[_RiskItem]]):
    pass


class RiskClassifier(Protocol):
    async def classify(self, batch: list[Clause], contract_type: str, norms_ctx: str) -> list[ClauseRiskResult] | None: ...


class LlmRiskClassifier:
    def __init__(self, llm: Completer):
        self.llm = llm

    @staticmethod
    def prompt(contract_type: str, norms_ctx: str, batch: list[Clause]) -> str:
        norms = ("(нормативная база не найдена — используй общие знания законодательства РК)\n"
                 if not norms_ctx.strip() else f"ПРИМЕНИМЫЕ НОРМЫ ИЗ БАЗЫ ЗАКОНОВ:\n{norms_ctx}\n")
        block = "".join(f"[{c.id}] {c.text}\n" for c in batch)
        return PROMPT.format(contract_type=contract_type, norms=norms, clauses=block)

    async def classify(self, batch, contract_type, norms_ctx):
        out = await self.llm.structured(SYSTEM, self.prompt(contract_type, norms_ctx, batch), _RiskItems)
        if out is None:
            return None
        texts = {c.id: c.text for c in batch}
        return [ClauseRiskResult(clause_id=i.clauseId or "", clause_text=texts.get(i.clauseId or "", ""),
                                 risk_level=normalize_level(i.riskLevel), law_citation=i.lawCitation, reason=i.reason)
                for i in out.root]


def format_chunk(chunk: dict) -> str:
    meta = chunk.get("metadata") or {}
    prefix = ""
    if meta.get("lawCode") is not None:
        art = f"ст. {meta['articleNumber']} " if meta.get("articleNumber") is not None else ""
        prefix = f"[{art}{meta['lawCode']}] "
    text = chunk.get("text") or ""
    return prefix + (text[:300] + "…" if len(text) > 300 else text)


class ContractRiskService:
    def __init__(self, laws: LawPort, segmenter: ClauseSegmenter, types: ContractTypeService,
                 risk: RiskClassifier, required: RequiredClauseChecker):
        self.laws, self.segmenter, self.types, self.risk, self.required = laws, segmenter, types, risk, required

    async def scan(self, text: str, country: str | None = "RK") -> RiskScanResponse:
        country = country if country and country.strip() else "RK"
        clauses = await self.segmenter.segment(text)
        contract_type = await self.types.detect(text)
        missing = await self.required.missing(text, contract_type, [c.text for c in clauses])
        if not clauses:
            log.warning("No clauses extracted from contract text (%d chars)", len(text))
            return RiskScanResponse(contract_type=contract_type, missing_clauses=missing)

        ctx = await self._norms_context(contract_type, country)
        results: list[ClauseRiskResult] = []
        for i in range(0, len(clauses), BATCH_SIZE):
            batch = clauses[i:i + BATCH_SIZE]
            try:
                part = await self.risk.classify(batch, contract_type, ctx)
            except Exception as e:
                log.error("risk analysis failed: %s", e)
                part = None
            if part is None:
                log.warning("risk classifier gave no result, using fallback")
                part = [ClauseRiskResult(clause_id=c.id, clause_text=c.text, risk_level="COMPLIANT") for c in batch]
            results += part

        levels = [r.risk_level for r in results]
        return RiskScanResponse(contract_type=contract_type, clauses=results, missing_clauses=missing,
                                total_violations=levels.count("VIOLATION"), total_warnings=levels.count("WARNING"),
                                total_compliant=len(levels) - levels.count("VIOLATION") - levels.count("WARNING"))

    async def _norms_context(self, contract_type: str, country: str) -> str:
        found: list[dict] = []
        for q in SEARCH_QUERIES.get(contract_type, DEFAULT_QUERIES):
            try:
                for r in await self.laws.search(q, country):
                    if len(found) >= MAX_CONTEXT_ARTICLES:
                        break
                    found.append(r)
            except Exception as e:
                log.warning("law search failed for '%s': %s", q, e)
        return "".join(format_chunk(c) + "\n\n" for c in found if isinstance(c, dict))
