import json
import logging
import re
from typing import AsyncIterator

from app.clients.ports import DocumentPort
from app.ports import DocumentEditor
from app.schemas import BrainRequest, BrainResponse, EditApplyResponse, EditProposal
from app.sse import Sse

log = logging.getLogger(__name__)

EDIT_COMMAND = re.compile(
    r"(?iu)\b(?:примени|применить|внеси|внести|исправь|исправить|поправь|отредактируй|"
    r"сделай\s+исправлен|подготовь\s+исправлен|create\s+fixed|apply)\w*"
    r"[^.?!]{0,40}?(?:измен|правк|исправлен|ошибк|документ|договор|fix|edit)")
RESULT_EVENT = "document-edit-result"


def is_edit_command(req: BrainRequest) -> bool:
    return req.has_document and bool(EDIT_COMMAND.search(req.message or ""))


def render(applied: EditApplyResponse | None, proposals: list[EditProposal]) -> str:
    if not proposals:
        return "Проверил документ: ошибок, которые можно исправить автоматически, не найдено. Документ не изменён."
    if applied is None or not applied.document_id:
        reasons = "; ".join(s.reason for s in (applied.skipped if applied else []))[:300]
        return "Не удалось внести исправления в документ." + (f" Причина: {reasons}" if reasons else "")
    by_id = {p.id: p for p in proposals}
    lines = [f"Внёс исправлений: {len(applied.applied)}. Создан документ «{applied.file_name}», "
             "оригинал не изменён.", "", "### Что исправлено", ""]
    for pid in applied.applied:
        p = by_id[pid]
        basis = f" ({p.law_basis})" if p.law_basis else ""
        mark = " _формулировка ИИ — проверьте_" if p.source == "llm" else ""
        lines.append(f"- **п. {p.clause}**{basis}: «{p.original}» → «{p.replacement}».{mark}")
    if applied.skipped:
        lines += ["", "### Не применено", ""]
        lines += [f"- п. {by_id[s.id].clause if s.id in by_id else '?'}: {s.reason}" for s in applied.skipped]
    return "\n".join(lines)


class DocumentEditHandler:
    def __init__(self, edits: DocumentEditor, documents: DocumentPort):
        self.edits, self.documents = edits, documents

    def matches(self, req: BrainRequest) -> bool:
        return is_edit_command(req)

    async def _run(self, req: BrainRequest) -> tuple[EditApplyResponse | None, list[EditProposal]]:
        text = await self.documents.text(req.document_id, req.user_id)
        if not text or not text.strip():
            raise LookupError("Текст документа недоступен.")
        proposals, _ = await self.edits.suggest_with_audit(text)
        if not proposals:
            return None, []
        return await self.edits.apply(req.document_id, req.user_id, proposals), proposals

    async def handle(self, req: BrainRequest) -> BrainResponse:
        try:
            applied, proposals = await self._run(req)
        except Exception as e:
            log.warning("Document edit command failed for %s: %s", req.document_id, e)
            return BrainResponse(content=f"Не удалось внести исправления: {e}", tokens=0)
        return BrainResponse(content=render(applied, proposals), tokens=0)

    async def stream(self, req: BrainRequest) -> AsyncIterator[Sse]:
        yield Sse.thinking({"step": "edits", "message": "Проверяю документ и готовлю исправления"})
        try:
            applied, proposals = await self._run(req)
        except Exception as e:
            log.warning("Document edit command failed for %s: %s", req.document_id, e)
            yield Sse(f"Не удалось внести исправления: {e}", "error")
            return
        if applied and applied.document_id:
            yield Sse(json.dumps({"documentId": applied.document_id, "fileName": applied.file_name,
                                  "applied": len(applied.applied)}, ensure_ascii=False), RESULT_EVENT)
        yield Sse(render(applied, proposals))
        yield Sse("", "done")
