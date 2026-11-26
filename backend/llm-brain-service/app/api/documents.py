import logging

from fastapi import APIRouter, Header, Response

from app.analysis.audit import ReferenceAuditService
from app.clients.ports import DocumentPort
from app.edits.service import DocumentEditService
from app.structure.service import StructureService, describe
from app.schemas import (AuditResponse, EditApplyRequest, EditApplyResponse, EditProposal, EditRewriteRequest,
                         EditSuggestResponse)

log = logging.getLogger(__name__)


def build_router(documents: DocumentPort, auditor: ReferenceAuditService,
                 edits: DocumentEditService | None = None, structure: StructureService | None = None) -> APIRouter:
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
        resp = (await auditor.audit(text)).response()
        await _pages(document_id, x_user_id, text, resp.findings)
        return resp

    async def _pages(document_id: str, user_id: str, text: str, *groups) -> None:
        if structure is None:
            return
        try:
            st = await structure.build(document_id, user_id, text, use_llm=False)
        except Exception as e:
            log.debug("structure for pages failed: %s", e)
            return
        for g in groups:
            structure.attach_pages(st, g)

    @router.get("/{document_id}/structure")
    async def document_structure(document_id: str, x_user_id: str | None = Header(None), llm: bool = True):
        if not x_user_id or structure is None:
            return Response(status_code=400 if not x_user_id else 501)
        text = await _text(document_id, x_user_id)
        if isinstance(text, Response):
            return text
        st = await structure.build(document_id, x_user_id, text, use_llm=llm)
        return {
            "documentId": document_id,
            "pages": [{"page": p.page, "start": p.start, "end": p.end} for p in st.pages],
            "sections": [{"number": x.number, "title": x.title, "start": x.start, "end": x.end, "page": x.page}
                         for x in st.sections],
            "clauses": [{"number": c.number, "start": c.start, "end": c.end, "page": c.page, "section": c.section,
                         "types": [{"code": t.code, "label": t.label, "source": t.source} for t in c.types],
                         "party": c.party, "modality": c.modality, "summary": describe(c),
                         "paragraphs": [{"start": p.start, "end": p.end, "page": p.page} for p in c.paragraphs]}
                        for c in st.clauses],
        }

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
        await _pages(document_id, x_user_id, text, proposals, audit.findings)
        return EditSuggestResponse(document_id=document_id, summary=audit.summary, proposals=proposals,
                                   markdown=audit.markdown, problems=audit.problems)

    @router.post("/{document_id}/edits/rewrite", response_model=EditProposal)
    async def rewrite_fragment(document_id: str, body: EditRewriteRequest, x_user_id: str | None = Header(None)):
        if not x_user_id or edits is None:
            return Response(status_code=400 if not x_user_id else 501)
        text = await _text(document_id, x_user_id)
        if isinstance(text, Response):
            return text
        proposal = await edits.rewrite(text, body.original, body.instruction)
        return proposal if proposal is not None else Response(status_code=422)

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
