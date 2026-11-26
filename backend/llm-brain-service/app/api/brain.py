from fastapi import APIRouter

from app.pipeline.brain import BrainPipeline
from app.schemas import BrainRequest, BrainResponse
from app.sse import sse_response


def build_router(pipeline: BrainPipeline) -> APIRouter:
    router = APIRouter(prefix="/api/brain", tags=["brain"])

    @router.post("/chat", response_model=BrainResponse, response_model_by_alias=True)
    async def chat(req: BrainRequest) -> BrainResponse:
        return await pipeline.answer(req)

    @router.post("/chat/stream")
    async def chat_stream(req: BrainRequest):
        return sse_response(pipeline.stream(req))

    return router
