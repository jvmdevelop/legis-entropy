from fastapi import APIRouter, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

router = APIRouter(prefix="/actuator", tags=["actuator"])


@router.get("/health")
async def health() -> dict:
    return {"status": "UP"}


@router.get("/prometheus")
async def prometheus() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
