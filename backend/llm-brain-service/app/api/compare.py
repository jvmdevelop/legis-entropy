from fastapi import APIRouter, Header

from app.analysis.compare import CompareService
from app.schemas import BrainResponse, CompareRequest


def build_router(service: CompareService) -> APIRouter:
    router = APIRouter(prefix="/api/brain", tags=["compare"])

    @router.post("/compare", response_model=BrainResponse)
    async def compare(req: CompareRequest, x_user_id: str | None = Header(None)):
        user = req.user_id if req.user_id and req.user_id.strip() else x_user_id
        return await service.compare(req.model_copy(update={"user_id": user}))

    return router
