import logging
from dataclasses import dataclass

from app.clients.ports import GraphPort
from app.graph.laws import default_country, law_text, rank
from app.graph.link.base import LinkableSubject, SubjectKind, SubjectLinker, SubjectLinkerRegistry
from app.graph.link.service import LinkResult, SubjectAwareLinkService
from app.ml.hybrid import HybridRanker
from app.schemas import BrainRequest

log = logging.getLogger(__name__)


@dataclass
class AddResult:
    added: bool
    code: str
    title: str | None
    report: str


class GraphActionService:
    def __init__(self, graph: GraphPort, registry: SubjectLinkerRegistry, links: SubjectAwareLinkService,
                 ranker: HybridRanker):
        self.graph, self.registry, self.links, self.ranker = graph, registry, links, ranker

    async def link_request_subject_to_laws(self, req: BrainRequest, limit: int = 5,
                                           country: str = "RK") -> LinkResult:
        linker = self.registry.select_for(req)
        if linker is None:
            return LinkResult(0, "В запросе не указан ни один объект для связывания "
                                 "(документ / ситуация / голосовое сообщение).")
        subject = await linker.resolve(req)
        if subject is None:
            return LinkResult(0, f"Не удалось извлечь {linker.display_accusative} из запроса.")
        return await self.links.link_subject_to_laws(linker, subject, req.graph_id, limit, country)

    async def _link(self, kind: SubjectKind, graph_id: str, subject_id: str, user_id: str | None, limit: int,
                    country: str, probe: BrainRequest | None = None) -> LinkResult:
        linker = self._require(kind)
        subject = (await linker.resolve(probe) if probe else None) or LinkableSubject(kind, subject_id, user_id or "")
        return await self.links.link_subject_to_laws(linker, subject, graph_id, limit, country)

    async def link_document_to_laws(self, graph_id: str, document_id: str, user_id: str | None, limit: int = 5,
                                    country: str = "RK") -> LinkResult:
        return await self._link(SubjectKind.DOCUMENT, graph_id, document_id, user_id, limit, country)

    async def link_situation_to_laws(self, graph_id: str, situation_id: str, user_id: str | None, limit: int = 5,
                                     country: str = "RK") -> LinkResult:
        probe = BrainRequest(message="", user_id=user_id or "", graph_id=graph_id, situation_id=situation_id)
        return await self._link(SubjectKind.SITUATION, graph_id, situation_id, user_id, limit, country, probe)

    async def link_voice_evidence_to_laws(self, graph_id: str, voice_id: str, user_id: str | None, limit: int = 5,
                                          country: str = "RK") -> LinkResult:
        probe = BrainRequest(message="", user_id=user_id or "", graph_id=graph_id, voice_id=voice_id)
        return await self._link(SubjectKind.VOICE_EVIDENCE, graph_id, voice_id, user_id, limit, country, probe)

    async def add_law_to_graph(self, graph_id: str, code: str, country: str | None = None) -> AddResult:
        ctry = default_country(country)
        if await self._try_add(graph_id, code, ctry):
            return AddResult(True, code, None, f"Закон {code} добавлен в граф.")
        try:
            found = await self.graph.search_laws(code, ctry)
        except Exception as e:
            log.warning("Fallback search failed for %s: %s", code, e)
            found = []
        if found and await self._try_add(graph_id, found[0]["code"], ctry):
            first = found[0]
            return AddResult(True, first["code"], first.get("title"),
                             f"Добавлен закон {first['code']}: {first.get('title')}")
        return AddResult(False, code, None, f"Не удалось добавить закон '{code}' — не найден в базе.")

    async def find_and_add_laws(self, graph_id: str, query: str, country: str | None = None,
                                limit: int = 5) -> LinkResult:
        ctry = default_country(country)
        limit = max(1, min(limit, 10))
        try:
            laws = [l for l in await self.graph.search_laws(query, ctry) if l.get("code")]
        except Exception as e:
            log.warning("Failed to execute findAndAddRelatedLawsToGraph: %s", e)
            return LinkResult(0, "Произошла ошибка при поиске и добавлении законов.")
        if not laws:
            return LinkResult(0, f"Ничего не найдено по запросу «{query}».")
        top = [law for law, _ in await rank(self.ranker, query, laws, law_text, limit)]
        codes, lines = [], ["Найдено и добавлено в граф:"]
        for law in top:
            if await self._try_add(graph_id, law["code"], ctry):
                codes.append(law["code"])
                lines.append(f"- **{law['code']}**" + (f": {law['title']}" if law.get("title") else ""))
        if not codes:
            return LinkResult(0, "Найдены законы, но ни один не удалось добавить в граф.", codes)
        return LinkResult(len(codes), "\n".join(lines) + "\n", codes)

    async def add_law_codes(self, graph_id: str, codes: list[str], country: str = "RK") -> list[str]:
        ctry = default_country(country)
        added: list[str] = []
        for code in dict.fromkeys(c.strip() for c in codes if c and c.strip()):
            if await self._try_add(graph_id, code, ctry):
                added.append(code)
            else:
                log.debug("Could not add law %s to graph %s", code, graph_id)
        return added

    def _require(self, kind: SubjectKind) -> SubjectLinker:
        linker = self.registry.by_kind(kind)
        if linker is None:
            raise RuntimeError(f"{kind.value} linker is not registered")
        return linker

    async def _try_add(self, graph_id: str, code: str, country: str) -> bool:
        try:
            await self.graph.add_law(graph_id, code, country)
            return True
        except Exception as e:
            log.debug("add law %s to graph %s failed: %s", code, graph_id, e)
            return False
