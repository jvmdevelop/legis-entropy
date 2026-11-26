from fastapi import APIRouter, Header

from app.analysis.compare import CompareService
from app.schemas import BrainResponse, CompareRequest


def build_router(service: CompareService) -> APIRouter:
    router = APIRouter(prefix="/api/brain", tags=["compare"])

    @router.post("/compare", response_model=BrainResponse)
    async def compare(req: CompareRequest, x_user_id: str | None = Header(None)):
        user = x_user_id.strip() if x_user_id and x_user_id.strip() else req.user_id
        return await service.compare(req.model_copy(update={"user_id": user}))

    return router
