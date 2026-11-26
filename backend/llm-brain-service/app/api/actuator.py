from fastapi import APIRouter, Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

router = APIRouter(prefix="/actuator", tags=["actuator"])


@router.api_route("/health", methods=["GET", "HEAD"])
async def health() -> dict:
    return {"status": "UP"}


@router.get("/prometheus")
async def prometheus() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
