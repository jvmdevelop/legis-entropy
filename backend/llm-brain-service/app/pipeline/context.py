from dataclasses import dataclass, field
from typing import Any

from app.schemas import RetrievalChunk

MAX_FRAGMENT_LENGTH = 2500
CONTEXT_BUDGET_CHARS = 12000
MIN_FRAGMENT = 300


@dataclass(frozen=True)
class Citation:
    id: str
    document_id: str | None = None
    file_name: str | None = None
    page: int | None = None
    chunk_index: int | None = None
    score: float | None = None
    article_number: int | None = None
    law_code: str | None = None
    law_document_number: str | None = None
    text: str = ""


@dataclass
class DocumentContext:
    text: str
    citations: list[Citation] = field(default_factory=list)
    unavailable: bool = False
    unavailable_reason: str | None = None

    @staticmethod
    def of(text: str, citations: list[Citation] | None = None) -> "DocumentContext":
        return DocumentContext(text, list(citations or []))

    @staticmethod
    def missing(reason: str) -> "DocumentContext":
        return DocumentContext(reason, [], True, reason)

    @property
    def has_citations(self) -> bool:
        return bool(self.citations)

    @property
    def sources(self) -> list[str]:
        return [c.text for c in self.citations if c.text and c.text.strip()]


def _meta(chunk: RetrievalChunk, key: str) -> Any:
    return (chunk.metadata or {}).get(key)


def _int_meta(chunk: RetrievalChunk, key: str) -> int | None:
    v = _meta(chunk, key)
    if v is None or isinstance(v, bool):
        return None
    if isinstance(v, (int, float)):
        return int(v)
    try:
        return int(str(v))
    except ValueError:
        return None


def _str_meta(chunk: RetrievalChunk, key: str) -> str | None:
    v = _meta(chunk, key)
    return None if v is None else str(v)


def _java_str(v: Any) -> str:
    if isinstance(v, bool):
        return str(v).lower()
    return str(v)


class CitationService:
    @staticmethod
    def citation_id(index: int, chunk: RetrievalChunk) -> str:
        if chunk.document_id is not None and chunk.chunk_index is not None:
            return f"doc-{chunk.document_id}-chunk-{chunk.chunk_index}"
        if chunk.chunk_index is not None:
            return f"chunk-{chunk.chunk_index}"
        return f"chunk-{index + 1}"

    def citation_for(self, index: int, chunk: RetrievalChunk, max_fragment: int = MAX_FRAGMENT_LENGTH) -> Citation:
        return Citation(self.citation_id(index, chunk), chunk.document_id, chunk.file_name, chunk.page,
                        chunk.chunk_index, chunk.score, _int_meta(chunk, "articleNumber"),
                        _str_meta(chunk, "lawCode"), _str_meta(chunk, "lawDocumentNumber"),
                        (chunk.text or "")[:max_fragment])

    @staticmethod
    def format_context_block(c: Citation, chunk: RetrievalChunk, max_fragment: int = MAX_FRAGMENT_LENGTH) -> str:
        out = []
        if c.article_number is not None and c.article_number > 0:
            head = f"=== [Статья {c.article_number}"
            if c.law_code and c.law_code.strip():
                head += f" | {c.law_code}"
            if c.law_document_number and c.law_document_number.strip():
                head += f" ({c.law_document_number})"
            out.append(head + "] ===\n\n")
        out.append(f"Фрагмент [{c.id}]\n")
        for label, value in (("documentId", c.document_id), ("fileName", c.file_name), ("page", c.page),
                             ("chunkIndex", c.chunk_index), ("score", c.score),
                             ("sourceType", _meta(chunk, "sourceType")),
                             ("sectionTitle", _meta(chunk, "sectionTitle"))):
            if value is not None:
                out.append(f"{label}: {_java_str(value)}\n")
        out.append("text:\n" + (chunk.text or "")[:max_fragment] + "\n\n")
        return "".join(out)


def to_chunks(raw: list[Any] | None) -> list[RetrievalChunk]:
    out = []
    for r in raw or []:
        if isinstance(r, RetrievalChunk):
            out.append(r)
        elif isinstance(r, dict):
            try:
                out.append(RetrievalChunk.model_validate({**r, "metadata": r.get("metadata") or {},
                                                          "text": r.get("text") or ""}))
            except Exception:
                continue
    return out


class DocumentContextFormatter:
    def __init__(self, citations: CitationService | None = None, max_fragment: int = MAX_FRAGMENT_LENGTH,
                 budget: int = CONTEXT_BUDGET_CHARS):
        self.citations = citations or CitationService()
        self.max_fragment = max_fragment
        self.budget = budget

    def format(self, chunks: list[RetrievalChunk] | None) -> DocumentContext:
        if not chunks:
            return DocumentContext.missing("Документ ещё индексируется или не найден в индексе пользователя.")
        parts, cits = [], []
        left = self.budget
        for i, chunk in enumerate(chunks):
            if chunk is None or not (chunk.text or "").strip():
                continue
            limit = min(self.max_fragment, left)
            if limit < MIN_FRAGMENT:
                break
            c = self.citations.citation_for(i, chunk, limit)
            block = self.citations.format_context_block(c, chunk, limit)
            cits.append(c)
            parts.append(block)
            left -= len(block)
        if not parts:
            return DocumentContext.missing("Документ найден, но его текстовые фрагменты пустые.")
        return DocumentContext.of("".join(parts), cits)
