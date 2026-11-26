import logging

from fastapi import APIRouter, Header, Response

from app.analysis.audit import ReferenceAuditService
from app.clients.ports import DocumentPort
from app.edits.service import DocumentEditService
from app.schemas import AuditResponse, EditApplyRequest, EditApplyResponse, EditSuggestResponse

log = logging.getLogger(__name__)


def build_router(documents: DocumentPort, auditor: ReferenceAuditService,
                 edits: DocumentEditService | None = None) -> APIRouter:
    router = APIRouter(prefix="/api/brain/documents", tags=["documents"])

    @router.post("/{document_id}/audit", response_model=AuditResponse)
    async def audit(document_id: str, x_user_id: str | None = Header(None)):
        if not x_user_id:
            return Response(status_code=400)
        try:
            text = await documents.text(document_id, x_user_id)
        except Exception as e:
            log.warning("Audit: could not load text of document %s for user %s: %s", document_id, x_user_id, e)
            return Response(status_code=404)
        if not text or not text.strip():
            return Response(status_code=422)
        return (await auditor.audit(text)).response()

    async def _text(document_id: str, user_id: str) -> str | Response:
        try:
            text = await documents.text(document_id, user_id)
        except Exception as e:
            log.warning("Edits: could not load document %s for user %s: %s", document_id, user_id, e)
            return Response(status_code=404)
        return text if text and text.strip() else Response(status_code=422)

    @router.post("/{document_id}/edits/suggest", response_model=EditSuggestResponse)
    async def suggest_edits(document_id: str, x_user_id: str | None = Header(None)):
        if not x_user_id or edits is None:
            return Response(status_code=400 if not x_user_id else 501)
        text = await _text(document_id, x_user_id)
        if isinstance(text, Response):
            return text
        proposals, audit = await edits.suggest_with_audit(text)
        return EditSuggestResponse(document_id=document_id, summary=audit.summary, proposals=proposals,
                                   markdown=audit.markdown, problems=audit.problems)

    @router.post("/{document_id}/edits/apply", response_model=EditApplyResponse)
    async def apply_edits(document_id: str, body: EditApplyRequest, x_user_id: str | None = Header(None)):
        if not x_user_id or edits is None:
            return Response(status_code=400 if not x_user_id else 501)
        try:
            return await edits.apply(document_id, x_user_id, body.proposals, body.title)
        except LookupError:
            return Response(status_code=404)
        except Exception as e:
            log.warning("Applying edits to %s failed: %s", document_id, e)
            return Response(status_code=502)

    return router
