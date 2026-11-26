import logging

from fastapi import APIRouter, Header, Response

from app.clients.ports import DocumentPort
from app.playbooks.service import TEMPLATES, PlaybookChecker, PlaybookStore
from app.schemas import Playbook, PlaybookCheckResponse
from app.structure.service import StructureService

log = logging.getLogger(__name__)


def build_router(store: PlaybookStore, checker: PlaybookChecker, documents: DocumentPort,
                 structure: StructureService) -> APIRouter:
    router = APIRouter(prefix="/api/brain", tags=["playbooks"])

    @router.get("/playbooks", response_model=list[Playbook])
    async def list_playbooks(x_user_id: str | None = Header(None)):
        if not x_user_id:
            return Response(status_code=400)
        return await store.list(x_user_id)

    @router.get("/playbooks/templates", response_model=list[Playbook])
    async def templates():
        return TEMPLATES

    @router.post("/playbooks", response_model=Playbook)
    async def save_playbook(body: Playbook, x_user_id: str | None = Header(None)):
        if not x_user_id:
            return Response(status_code=400)
        if not body.name.strip() or not body.rules:
            return Response(status_code=422)
        return await store.save(x_user_id, body)

    @router.delete("/playbooks/{playbook_id}")
    async def delete_playbook(playbook_id: str, x_user_id: str | None = Header(None)):
        if not x_user_id:
            return Response(status_code=400)
        return Response(status_code=204 if await store.delete(x_user_id, playbook_id) else 404)

    @router.post("/documents/{document_id}/playbooks/{playbook_id}/check", response_model=PlaybookCheckResponse)
    async def check(document_id: str, playbook_id: str, x_user_id: str | None = Header(None)):
        if not x_user_id:
            return Response(status_code=400)
        playbook = await store.get(x_user_id, playbook_id)
        if playbook is None:
            return Response(status_code=404)
        try:
            text = await documents.text(document_id, x_user_id)
        except Exception as e:
            log.warning("playbook check: document %s unavailable: %s", document_id, e)
            return Response(status_code=404)
        if not text or not text.strip():
            return Response(status_code=422)
        st = await structure.build(document_id, x_user_id, text)
        return await checker.check(playbook, st, text)

    return router
