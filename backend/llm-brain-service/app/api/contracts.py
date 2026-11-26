from fastapi import APIRouter, Response

from app.analysis.contract_risk import ContractRiskService
from app.schemas import RiskScanRequest, RiskScanResponse


def build_router(risk: ContractRiskService) -> APIRouter:
    router = APIRouter(prefix="/api/brain/contracts", tags=["contracts"])

    @router.post("/risk-scan", response_model=RiskScanResponse)
    async def risk_scan(req: RiskScanRequest | None = None):
        if req is None or not req.text or not req.text.strip():
            return Response(status_code=400)
        return await risk.scan(req.text, req.country)

    return router
