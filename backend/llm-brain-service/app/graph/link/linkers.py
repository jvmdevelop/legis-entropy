import json
import logging

from app.clients.ports import DocumentPort, GraphPort, SituationPort, VoicePort
from app.graph.link.base import LinkableSubject, SubjectKind, SubjectLinker
from app.schemas import BrainRequest

log = logging.getLogger(__name__)


async def _ok(coro, what: str) -> bool:
    try:
        await coro
        return True
    except Exception as e:
        log.warning("%s failed: %s", what, e)
        return False


class DocumentSubjectLinker(SubjectLinker):
    kind = SubjectKind.DOCUMENT
    priority = 20
    display_name, display_accusative, display_genitive = "Документ", "документ", "документа"

    def __init__(self, documents: DocumentPort, graph: GraphPort, situations: SituationPort):
        self.documents, self.graph, self.situations = documents, graph, situations

    def can_handle(self, req: BrainRequest) -> bool:
        return req.has_document

    async def resolve(self, req: BrainRequest) -> LinkableSubject | None:
        return LinkableSubject(self.kind, req.document_id, req.user_id or "") if self.can_handle(req) else None

    async def fetch_text(self, subject: LinkableSubject, graph_id: str | None) -> str:
        try:
            text = await self.documents.text(subject.id, subject.user_id)
            if text and text.strip():
                return text
        except Exception as e:
            log.debug("document text failed for %s: %s", subject.id, e)
        try:
            chunks = await self.documents.search("договор", subject.user_id, subject.id)
            return " ".join(c.get("text") or "" for c in chunks[:5])
        except Exception:
            return ""

    async def link_to_law(self, graph_id, subject, law_code, country) -> bool:
        return await _ok(self.graph.link_document(graph_id, subject.id, law_code, country, "SEMANTIC"),
                         f"link document {subject.id} -> {law_code}")

    async def link_clause_to_article(self, graph_id, subject, law_code, country, article_number, clause_ref,
                                     subject_snippet, article_snippet, confidence) -> bool:
        return await _ok(self.graph.link_clause({
            "graphId": graph_id, "documentId": subject.id, "lawCode": law_code, "country": country,
            "articleNumber": article_number, "clauseRef": clause_ref, "documentSnippet": subject_snippet,
            "articleSnippet": article_snippet, "extractedBy": "deep-analysis", "confidence": confidence,
        }), "link document clause to article")

    async def flag_conflict(self, graph_id, subject, law_code, country, article_number, clause_ref, reason,
                            confidence) -> bool:
        return await _ok(self.situations.flag_document_conflict({
            "graphId": graph_id, "documentId": subject.id, "lawCode": law_code, "country": country,
            "articleNumber": article_number, "clauseRef": clause_ref, "reason": reason, "confidence": confidence,
        }), "flag document-article conflict")


class SituationSubjectLinker(SubjectLinker):
    kind = SubjectKind.SITUATION
    priority = 10
    display_name, display_accusative, display_genitive = "Ситуация", "ситуация", "ситуации"

    def __init__(self, situations: SituationPort):
        self.situations = situations

    def can_handle(self, req: BrainRequest) -> bool:
        return req.has_situation and req.has_graph

    async def resolve(self, req: BrainRequest) -> LinkableSubject | None:
        if not self.can_handle(req):
            return None
        try:
            s = await self.situations.get(req.graph_id, req.situation_id)
        except Exception as e:
            log.warning("Failed to resolve situation %s in graph %s: %s", req.situation_id, req.graph_id, e)
            return None
        if not s:
            return None
        title = s.get("title")
        return LinkableSubject(self.kind, req.situation_id, req.user_id or "", str(title) if title else None)

    async def fetch_text(self, subject: LinkableSubject, graph_id: str | None) -> str:
        if not graph_id or not graph_id.strip():
            return ""
        try:
            s = await self.situations.get(graph_id, subject.id) or {}
        except Exception as e:
            log.warning("Failed to fetch searchable text for situation %s: %s", subject.id, e)
            return ""
        plain = s.get("plainText")
        return str(plain) if plain and str(plain).strip() else str(s.get("body") or "")

    async def link_to_law(self, graph_id, subject, law_code, country) -> bool:
        return await _ok(self.situations.link_law(graph_id, subject.id, law_code, country, "SEMANTIC"),
                         f"link situation {subject.id} -> {law_code}")


class VoiceEvidenceSubjectLinker(SubjectLinker):
    kind = SubjectKind.VOICE_EVIDENCE
    priority = 0
    display_name, display_accusative, display_genitive = (
        "Голосовое сообщение", "голосовое сообщение", "голосового сообщения")

    def __init__(self, voice: VoicePort, graph: GraphPort):
        self.voice, self.graph = voice, graph

    async def _get(self, voice_id: str) -> dict:
        try:
            return await self.voice.get(voice_id) or {}
        except Exception as e:
            log.debug("getVoiceMessage failed for %s: %s", voice_id, e)
            return {}

    def can_handle(self, req: BrainRequest) -> bool:
        return req.has_voice

    async def resolve(self, req: BrainRequest) -> LinkableSubject | None:
        if not self.can_handle(req):
            return None
        fn = (await self._get(req.voice_id)).get("fileName")
        return LinkableSubject(self.kind, req.voice_id, req.user_id or "", str(fn) if fn else None)

    async def fetch_text(self, subject: LinkableSubject, graph_id: str | None) -> str:
        m = await self._get(subject.id)
        t = m.get("transcript")
        return str(t) if t and str(t).strip() else str(m.get("analysisJson") or "")

    async def link_to_law(self, graph_id, subject, law_code, country) -> bool:
        return await _ok(self.graph.link_voice(graph_id, subject.id, law_code, country,
                                               "Suggested by AI from voice analysis"),
                         f"link voice {subject.id} -> {law_code}")

    async def suggested_law_codes(self, subject: LinkableSubject, graph_id: str | None) -> list[str]:
        raw = (await self._get(subject.id)).get("analysisJson")
        try:
            data = json.loads(raw) if isinstance(raw, str) else raw
            arts = (data or {}).get("articles") or []
        except (ValueError, AttributeError):
            return []
        seen, out = set(), []
        for a in arts if isinstance(arts, list) else []:
            code = str((a or {}).get("lawCode") or "").strip() if isinstance(a, dict) else ""
            if code and code.upper() not in seen:
                seen.add(code.upper())
                out.append(code)
        return out
