import logging
from contextlib import asynccontextmanager
from dataclasses import dataclass
from datetime import datetime

from fastapi import APIRouter, FastAPI, Request
from fastapi.responses import JSONResponse

from app import discovery
from app.analysis import wiring as analysis_wiring
from app.api import actuator
from app.config import Settings, get_settings
from app.container import Container, build_container
from app.graph import wiring as graph_wiring
from app.llm.messages import MessageBuilder
from app.pipeline import wiring as pipeline_wiring
from app.prompt import instructions

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")
log = logging.getLogger("llm-brain-service")


@dataclass
class Application:
    container: Container
    routers: list[APIRouter]


def compose(c: Container) -> Application:
    analysis = analysis_wiring.wire(c)
    graph = graph_wiring.wire(c, auditor=analysis.audit)
    pipeline = pipeline_wiring.wire(c, reports=analysis.verifier, graph_writer=graph.actions,
                                     law_linker=graph.law_linker, edits=analysis.edits)
    return Application(c, [actuator.router, *analysis.routers, *pipeline.routers])


def default_container(s: Settings) -> Container:
    return build_container(s, messages=MessageBuilder(instructions.SYSTEM_PROMPT, list(instructions.FEW_SHOT)))


def _error(status: int, message: str) -> JSONResponse:
    return JSONResponse(status_code=status,
                        content={"status": status, "message": message, "timestamp": datetime.now().isoformat()})


def create_app(application: Application | None = None, settings: Settings | None = None) -> FastAPI:
    settings = settings or get_settings()
    application = application or compose(default_container(settings))

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        await discovery.register(settings)
        yield
        await discovery.unregister(settings)
        await application.container.aclose()

    app = FastAPI(title="llm-brain-service", lifespan=lifespan)
    for r in application.routers:
        app.include_router(r)

    @app.exception_handler(ValueError)
    async def _bad_request(_: Request, e: ValueError):
        return _error(400, str(e) or "Error")

    @app.exception_handler(Exception)
    async def _unexpected(_: Request, e: Exception):
        log.exception("Unexpected error in llm-brain-service")
        return _error(500, "An unexpected error occurred")

    return app


app = create_app()
