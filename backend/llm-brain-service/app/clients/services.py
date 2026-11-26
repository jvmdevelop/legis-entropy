import re
from typing import Any
from urllib.parse import quote, unquote

from app.clients.base import ServiceClient

Json = dict[str, Any]


def _attachment_name(header: str | None) -> str | None:
    if not header:
        return None
    m = re.search(r"filename\*=UTF-8''([^;]+)", header, re.I)
    if m:
        return unquote(m.group(1))
    m = re.search(r'filename="?([^";]+)"?', header, re.I)
    return m.group(1) if m else None


def _p(v: str) -> str:
    return quote(str(v), safe="")


class DocumentClient:
    def __init__(self, c: ServiceClient):
        self.c = c

    async def search(self, query: str, user_id: str | None, document_id: str | None = None) -> list[Json]:
        return await self.c.get("/api/user-documents/search", params={"query": query, "documentId": document_id},
                                headers={"X-User-Id": user_id}) or []

    async def text(self, document_id: str, user_id: str | None) -> str | None:
        r = await self.c.get(f"/api/user-documents/{_p(document_id)}/text", headers={"X-User-Id": user_id})
        return (r.get("text") or r.get("plainText")) if isinstance(r, dict) else None

    async def file_name(self, document_id: str, user_id: str | None) -> str | None:
        r = await self.c.get(f"/api/user-documents/{_p(document_id)}/status", headers={"X-User-Id": user_id})
        return r.get("fileName") if isinstance(r, dict) else None

    async def upload(self, user_id: str, file_name: str, content: bytes, content_type: str) -> str | None:
        r = await self.c.post("/api/user-documents/upload", headers={"X-User-Id": user_id},
                              files={"file": (file_name, content, content_type)})
        return r.get("documentId") if isinstance(r, dict) else None

    async def edited_copy(self, document_id: str, user_id: str, edits: list[Json],
                          file_name: str | None = None) -> Json | None:
        r = await self.c.post(f"/api/user-documents/{_p(document_id)}/edited-copy", headers={"X-User-Id": user_id},
                              json={"edits": edits, "fileName": file_name})
        return r if isinstance(r, dict) else None

    async def pages(self, document_id: str, user_id: str) -> list[Json]:
        r = await self.c.get(f"/api/user-documents/{_p(document_id)}/pages", headers={"X-User-Id": user_id})
        return (r or {}).get("pages") or [] if isinstance(r, dict) else []

    async def file(self, document_id: str, user_id: str) -> tuple[bytes, str | None, str | None]:
        r = await self.c.raw("GET", f"/api/user-documents/{_p(document_id)}/file", headers={"X-User-Id": user_id})
        return r.content, _attachment_name(r.headers.get("content-disposition")), r.headers.get("content-type")


class GraphClient:
    def __init__(self, c: ServiceClient):
        self.c = c

    async def law(self, code: str, country: str) -> Json | None:
        return await self.c.get(f"/api/graph/laws/{_p(code)}", params={"country": country})

    async def related(self, code: str, country: str) -> list[Json]:
        return await self.c.get(f"/api/graph/laws/{_p(code)}/related", params={"country": country}) or []

    async def search_laws(self, query: str, country: str | None) -> list[Json]:
        return await self.c.post("/api/graph/laws/search", json={"query": query, "country": country}) or []

    async def articles(self, code: str, country: str) -> list[Json]:
        return await self.c.get(f"/api/graph/laws/{_p(code)}/articles", params={"country": country}) or []

    async def article(self, code: str, number: str, country: str) -> Json | None:
        return await self.c.get(f"/api/graph/laws/{_p(code)}/articles/{_p(number)}", params={"country": country})

    async def search_articles(self, query: str, country: str | None) -> list[Json]:
        return await self.c.post("/api/graph/articles/search", json={"query": query, "country": country}) or []

    async def link_clause(self, req: Json) -> None:
        await self.c.post("/api/graph/articles/link-document", json=req)

    async def add_law(self, graph_id: str, code: str, country: str) -> None:
        await self.c.post(f"/api/v1/user-graphs/{_p(graph_id)}/laws/{_p(code)}", params={"country": country})

    async def link_document(self, graph_id: str, document_id: str, law_code: str, country: str, kind: str) -> None:
        await self.c.post(f"/api/v1/user-graphs/{_p(graph_id)}/documents/{_p(document_id)}/links/{_p(law_code)}",
                          params={"country": country, "kind": kind})

    async def link_voice(self, graph_id: str, voice_id: str, law_code: str, country: str, reason: str) -> None:
        await self.c.post(f"/api/v1/user-graphs/{_p(graph_id)}/voice-evidence/{_p(voice_id)}/laws/{_p(law_code)}",
                          params={"country": country, "reason": reason})

    async def create_comment(self, graph_id: str, req: Json) -> Json | None:
        return await self.c.post(f"/api/v1/user-graphs/{_p(graph_id)}/comments", json=req)


class LawClient:
    def __init__(self, c: ServiceClient):
        self.c = c

    async def search(self, query: str, country: str | None = None) -> list[Json]:
        if country:
            return await self.c.get(f"/api/laws/search/{_p(country)}", params={"query": query}) or []
        return await self.c.get("/api/laws/search", params={"query": query}) or []

    async def lookup_articles(self, law_code: str, numbers: list[str], country: str,
                              max_chars: int | None = None) -> list[Json]:
        return await self.c.get("/api/laws/articles/lookup",
                                params={"lawCode": law_code, "numbers": numbers, "country": country,
                                        "maxChars": max_chars}) or []

    async def article_titles(self, law_code: str, country: str) -> list[Json]:
        return await self.c.get("/api/laws/articles/titles", params={"lawCode": law_code, "country": country}) or []


class SituationClient:
    def __init__(self, c: ServiceClient):
        self.c = c

    async def flag_article_conflict(self, req: Json) -> None:
        await self.c.post("/api/graph/conflicts/article-article", json=req)

    async def flag_document_conflict(self, req: Json) -> None:
        await self.c.post("/api/graph/conflicts/document-article", json=req)

    async def create(self, graph_id: str, user_id: str | None, title: str, text: str) -> str | None:
        r = await self.c.post(f"/api/v1/user-graphs/{_p(graph_id)}/situations", headers={"X-User-Id": user_id},
                              json={"title": title or "Ситуация", "body": text, "plainText": text})
        return r.get("id") if isinstance(r, dict) else None

    async def set_generated_doc(self, graph_id: str, situation_id: str, doc_id: str, doc_title: str) -> None:
        await self.c.post(f"/api/v1/user-graphs/{_p(graph_id)}/situations/{_p(situation_id)}/generated-doc",
                          json={"docId": doc_id, "docTitle": doc_title})

    async def get(self, graph_id: str, situation_id: str) -> Json | None:
        return await self.c.get(f"/api/v1/user-graphs/{_p(graph_id)}/situations/{_p(situation_id)}")

    async def link_law(self, graph_id: str, situation_id: str, law_code: str, country: str, kind: str) -> None:
        await self.c.post(f"/api/v1/user-graphs/{_p(graph_id)}/situations/{_p(situation_id)}/laws/{_p(law_code)}",
                          params={"country": country, "kind": kind})


class TemplateClient:
    def __init__(self, c: ServiceClient):
        self.c = c

    async def create(self, user_id: str | None, template: Json) -> str | None:
        r = await self.c.post("/api/templates", headers={"X-User-Id": user_id}, json=template)
        return r.get("id") if isinstance(r, dict) else None

    async def render(self, template_id: str, user_id: str | None, title: str, variables: Json,
                     graph_id: str | None, situation_id: str | None) -> str | None:
        r = await self.c.post(f"/api/templates/{_p(template_id)}/render", headers={"X-User-Id": user_id},
                              json={"title": title, "variables": variables, "graphId": graph_id,
                                    "situationId": situation_id})
        return r.get("id") if isinstance(r, dict) else None


class VoiceClient:
    def __init__(self, c: ServiceClient):
        self.c = c

    async def get(self, voice_id: str) -> Json | None:
        return await self.c.get(f"/api/voice-messages/{_p(voice_id)}")
