import asyncio
import logging
import re
from dataclasses import dataclass

from app.analysis.references import line_around
from app.clients.ports import GraphPort

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class RepealedAct:
    number: str
    title: str
    status: str


@dataclass(frozen=True)
class RepealedMention:
    act: RepealedAct
    clause: str


def _norm(number: str) -> str:
    return re.sub(r"\s", "", number).replace("І", "I").upper()


REGISTRY: dict[str, RepealedAct] = {_norm(a.number): a for a in [
    RepealedAct("493-I", "Закон РК от 10 декабря 1999 года «О труде в Республике Казахстан»",
                "утратил силу с принятием Трудового кодекса РК 2007 года; сейчас действует Трудовой кодекс РК от 23.11.2015 № 414-V"),
    RepealedAct("251-III", "Трудовой кодекс РК от 15 мая 2007 года",
                "утратил силу; действует Трудовой кодекс РК от 23.11.2015 № 414-V"),
    RepealedAct("263-I", "Закон РК от 20 июня 1997 года «О пенсионном обеспечении в Республике Казахстан»",
                "утратил силу (заменён Законом РК от 21.06.2013 № 105-V, который, в свою очередь, заменён Социальным кодексом РК от 20.04.2023 № 224-VII)"),
    RepealedAct("105-V", "Закон РК от 21 июня 2013 года «О пенсионном обеспечении в Республике Казахстан»",
                "утратил силу с 1 июля 2023 года в связи с принятием Социального кодекса РК от 20.04.2023 № 224-VII"),
    RepealedAct("405-II", "Закон РК от 25 апреля 2003 года «Об обязательном социальном страховании»",
                "утратил силу с 1 января 2020 года Законом РК от 26.12.2019 № 286-VI (adilet.zan.kz Z030000405_); "
                "сейчас сфера регулируется Социальным кодексом РК от 20.04.2023 № 224-VII"),
]}

ACT_NUMBER = re.compile(r"(?iu)(?:№|N)\s*(\d+\s*-\s*[IVXІ]+)(?![^\W\d_])")
_REPEALED_STATUS = re.compile(r"(?iu)repeal|утрат|недейств|не\s+действ|inactive|expired|invalid|cancel|отмен")


def registry_find(text: str | None) -> list[RepealedMention]:
    found: dict[str, RepealedMention] = {}
    for m in ACT_NUMBER.finditer(text or ""):
        act = REGISTRY.get(_norm(m.group(1)))
        if act:
            found.setdefault(act.number, RepealedMention(act, line_around(text, m.start())))
    return list(found.values())


class RepealedActChecker:
    def __init__(self, graph: GraphPort | None = None, country: str = "RK", max_lookups: int = 10):
        self.graph, self.country, self.max_lookups = graph, country, max_lookups

    async def _graph_status(self, number: str) -> tuple[bool, str, str] | None:
        if self.graph is None:
            return None
        try:
            resp = await self.graph.law(number, self.country)
        except Exception as e:
            log.debug("graph law lookup failed for %s: %s", number, e)
            return None
        law = (resp.get("law") or resp) if isinstance(resp, dict) else None
        status = (law or {}).get("status")
        if not status:
            return None
        return bool(_REPEALED_STATUS.search(str(status))), law.get("title") or "", str(status)

    async def find(self, text: str | None) -> list[RepealedMention]:
        if not text or not text.strip():
            return []
        mentions: dict[str, int] = {}
        for m in ACT_NUMBER.finditer(text):
            mentions.setdefault(_norm(m.group(1)), m.start())
        numbers = list(mentions)[:self.max_lookups]
        statuses = await asyncio.gather(*(self._graph_status(n) for n in numbers))
        out: list[RepealedMention] = []
        for number, st in zip(numbers, statuses):
            known = REGISTRY.get(number)
            if st is None:
                act = known
            elif st[0]:
                act = RepealedAct(known.number if known else number, known.title if known else st[1] or f"Акт № {number}",
                                  known.status if known else f"утратил силу (статус в базе: {st[2]})")
            else:
                act = None
            if act:
                out.append(RepealedMention(act, line_around(text, mentions[number])))
        return out
