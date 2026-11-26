from fastapi import APIRouter, Header

from app.pipeline.brain import BrainPipeline
from app.schemas import BrainRequest, BrainResponse
from app.sse import sse_response


def with_caller(req: BrainRequest, x_user_id: str | None) -> BrainRequest:
    if x_user_id and x_user_id.strip():
        return req.model_copy(update={"user_id": x_user_id.strip()})
    return req


def build_router(pipeline: BrainPipeline) -> APIRouter:
    router = APIRouter(prefix="/api/brain", tags=["brain"])

    @router.post("/chat", response_model=BrainResponse, response_model_by_alias=True)
    async def chat(req: BrainRequest, x_user_id: str | None = Header(None)) -> BrainResponse:
        return await pipeline.answer(with_caller(req, x_user_id))

    @router.post("/chat/stream")
    async def chat_stream(req: BrainRequest, x_user_id: str | None = Header(None)):
        return sse_response(pipeline.stream(with_caller(req, x_user_id)))

    return router
