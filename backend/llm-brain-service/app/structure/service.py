import logging
from collections import OrderedDict

from app.clients.ports import DocumentPort
from app.llm.client import Completer
from app.structure.classify import classify
from app.structure.parser import DocumentStructure, Page, parse

log = logging.getLogger(__name__)
CACHE_SIZE = 64


def describe(clause) -> str:
    codes = {t.code for t in clause.types}
    who = clause.party or "сторона"
    if "TERMINATION" in codes and clause.modality == "RIGHT":
        return f"право на расторжение / односторонний отказ: {who}"
    if "TERMINATION" in codes and clause.modality == "PROHIBITION":
        return f"запрет на расторжение / отказ: {who}"
    if clause.modality == "OBLIGATION":
        return f"обязанность: {who}"
    if clause.modality == "RIGHT":
        return f"право: {who}"
    if clause.modality == "PROHIBITION":
        return f"запрет: {who}"
    return ", ".join(t.label for t in clause.types)


class StructureService:
    def __init__(self, documents: DocumentPort, llm: Completer | None = None):
        self.documents, self.llm = documents, llm
        self._cache: OrderedDict[tuple[str, bool], DocumentStructure] = OrderedDict()

    async def _pages(self, document_id: str, user_id: str, text: str) -> list[Page]:
        try:
            raw = await self.documents.pages(document_id, user_id)
            pages = [Page(int(p["page"]), int(p["start"]), int(p["end"])) for p in raw or []]
            if pages:
                return pages
        except Exception as e:
            log.debug("pages unavailable for %s: %s", document_id, e)
        return [Page(1, 0, len(text))]

    async def build(self, document_id: str, user_id: str, text: str | None = None,
                    use_llm: bool = True) -> DocumentStructure:
        key = (document_id, use_llm)
        if key in self._cache:
            self._cache.move_to_end(key)
            return self._cache[key]
        text = text if text is not None else (await self.documents.text(document_id, user_id) or "")
        structure = parse(text, await self._pages(document_id, user_id, text))
        await classify(structure.clauses, self.llm if use_llm else None)
        self._cache[key] = structure
        while len(self._cache) > CACHE_SIZE:
            self._cache.popitem(last=False)
        return structure

    def attach_pages(self, structure: DocumentStructure, items) -> None:
        for it in items:
            c = structure.clause(getattr(it, "clause", None))
            if c is not None:
                it.page = c.page
