import logging
from dataclasses import dataclass, field, replace
from typing import Protocol

from app.clients.ports import GraphPort
from app.graph.laws import canonical, is_low_signal, law_text, parse_string_array, rank
from app.graph.link.base import LinkableSubject, SubjectLinker
from app.graph.link.service import keyword_queries
from app.ml.hybrid import HybridRanker
from app.llm.client import Completer

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class PrimaryLawContext:
    doc_text: str
    country: str
    linker: SubjectLinker
    subject: LinkableSubject
    graph_id: str
    target_count: int
    already: dict[str, dict] = field(default_factory=dict)


class PrimaryLawSource(Protocol):
    order: int
    name: str

    def should_run(self, ctx: PrimaryLawContext) -> bool: ...

    async def collect(self, ctx: PrimaryLawContext) -> list[dict]: ...


def _stub(code: str) -> dict:
    return {"code": code, "title": code}


LLM_PRIMARY_PROMPT = """Ты — юрист Республики Казахстан. ОТВЕЧАЙ ТОЛЬКО по законам РК.
Запрещено ссылаться на ТК РФ / УК РФ / ГК РФ / законы РСФСР / СССР / ЕС
и любые другие юрисдикции — только РК.

Прочитай содержимое {subject} (может быть разговорной речью, угрозами,
трудовыми конфликтами, нецензурной лексикой) и определи 3–6
нормативных актов РК, применимых к описанной ситуации.

Используй СТРОГО официальные сокращения кодексов РК:
  "УК РК"   — Уголовный кодекс
  "ТК РК"   — Трудовой кодекс
  "ГК РК"   — Гражданский кодекс
  "КоАП РК" — Кодекс об административных правонарушениях
  "НК РК"   — Налоговый кодекс
  "СК РК"   — Кодекс «О браке (супружестве) и семье»
  или "Закон РК О …" — для отдельных законов РК.

Верни СТРОГО валидный JSON-массив строк, без какого-либо текста
до или после, без markdown-fences. Если ничего применимого нет — верни [].

Пример валидного ответа:
["УК РК","ТК РК","КоАП РК"]

Содержимое:
{text}
"""


class LlmFallbackSource:
    order, name = 5, "llm-primary"

    def __init__(self, llm: Completer):
        self.llm = llm

    def should_run(self, ctx: PrimaryLawContext) -> bool:
        return True

    async def collect(self, ctx: PrimaryLawContext) -> list[dict]:
        if not ctx.doc_text.strip():
            return []
        prompt = LLM_PRIMARY_PROMPT.format(subject=ctx.linker.display_genitive, text=ctx.doc_text[:2000])
        try:
            codes = parse_string_array(await self.llm.complete("", prompt))
        except Exception as e:
            log.warning("LlmFallbackSource failed: %s", e)
            return []
        log.info("LlmFallbackSource produced %d code(s): %s", len(codes), codes)
        return [_stub(c) for c in codes]


KEYWORD_MARKERS = [
    ("ходатайств", "ГПК РК отложение судебного заседания"), ("смэс", "ГПК РК экономический суд"),
    ("заседани", "ГПК РК судебное заседание"), ("отложен", "ГПК РК отложение заседания"),
    ("перенос", "ГПК РК отложение заседания"), ("иск", "ГПК РК исковое заявление"), ("подсудност", "подсудность"),
    ("арбитраж", "арбитраж"), ("апелляц", "ГПК РК апелляция"), ("кассац", "ГПК РК кассация"),
    ("возмездного оказания услуг", "возмездное оказание услуг"), ("оказани", "оказание услуг"),
    ("договор", "договор"), ("поставк", "поставка"), ("аренд", "аренда"), ("подряд", "подряд"),
    ("купли-продаж", "купля-продажа"), ("займ", "займ"), ("кредит", "кредит"), ("акционерн", "акционерное общество"),
    ("товарищество", "товарищество"), ("страхов", "страхование"), ("наслед", "наследование"),
    ("брак", "семейное право"), ("исковая давность", "исковая давность"), ("неустойк", "неустойка"),
    ("пени", "пени"), ("спор", "разрешение споров"), ("претензи", "претензия"),
    ("конфиденциальн", "конфиденциальность"), ("персональн", "персональные данные"),
    ("интеллектуальн", "интеллектуальная собственность"), ("авторск", "авторское право"),
    ("товарн", "товарный знак"), ("программ", "программное обеспечение"), ("налог", "налог"),
    ("трудово", "трудовой кодекс"), ("работник", "трудовой кодекс"), ("работодател", "трудовой кодекс"),
    ("увол", "трудовой кодекс увольнение"), ("безработн", "трудовой кодекс"),
    ("работать бесплатно", "трудовой кодекс оплата труда"), ("бесплатно", "трудовой кодекс оплата труда"),
    ("зарплат", "трудовой кодекс заработная плата"), ("заработн", "трудовой кодекс заработная плата"),
    ("премии", "трудовой кодекс премии"), ("переработк", "трудовой кодекс сверхурочная"),
    ("выходн", "трудовой кодекс отдых"), ("отпуск", "трудовой кодекс отпуск"), ("уголовн", "уголовный кодекс"),
    ("гражданск", "гражданский кодекс"), ("угроз", "уголовный кодекс угроза"),
    ("вымогат", "уголовный кодекс вымогательство"), ("шантаж", "уголовный кодекс вымогательство"),
    ("принужд", "уголовный кодекс принуждение"), ("оскорб", "административный кодекс оскорбление"),
    ("клевет", "защита чести и достоинства"), ("мошенн", "уголовный кодекс мошенничество"),
    ("обман", "уголовный кодекс мошенничество"), ("побои", "уголовный кодекс побои"),
    ("насили", "уголовный кодекс насилие"), ("штраф", "административный штраф"),
]


class KeywordSearchSource:
    order, name = 20, "keyword-search"

    def __init__(self, graph: GraphPort, ranker: HybridRanker):
        self.graph, self.ranker = graph, ranker

    def should_run(self, ctx: PrimaryLawContext) -> bool:
        return not ctx.already

    async def collect(self, ctx: PrimaryLawContext) -> list[dict]:
        queries = keyword_queries(ctx.doc_text, KEYWORD_MARKERS, title_line=False) if ctx.doc_text.strip() \
            else ["договор"]
        found: dict[str, dict] = {}
        for q in queries:
            try:
                for law in await self.graph.search_laws(q, ctx.country):
                    if law.get("code") and not is_low_signal(law):
                        found.setdefault(law["code"], law)
            except Exception as e:
                log.debug("searchLaws('%s') failed: %s", q, e)
        return [l for l, _ in await rank(self.ranker, ctx.doc_text[:2000], list(found.values()), law_text,
                                         ctx.target_count * 3)]


class SubjectSeedSource:
    order, name = 20, "subject-seed"

    def should_run(self, ctx: PrimaryLawContext) -> bool:
        return True

    async def collect(self, ctx: PrimaryLawContext) -> list[dict]:
        seeds = await ctx.linker.suggested_law_codes(ctx.subject, ctx.graph_id)
        return [_stub(c) for c in seeds if c and c.strip()]


class PrimaryLawResolver:
    def __init__(self, sources: list[PrimaryLawSource], graph: GraphPort):
        self.sources = sorted(sources, key=lambda s: s.order)
        self.graph = graph

    async def resolve(self, seed: PrimaryLawContext) -> dict[str, dict]:
        hits: dict[str, dict] = {}
        for src in self.sources:
            ctx = replace(seed, already=dict(hits))
            if not src.should_run(ctx):
                continue
            try:
                before = len(hits)
                for law in await src.collect(ctx):
                    await self._merge(hits, law, ctx.country)
                log.info("PrimaryLawResolver %s contributed %d new law(s); total=%d", src.name,
                         len(hits) - before, len(hits))
            except Exception as e:
                log.warning("PrimaryLawResolver source %s failed: %s", src.name, e)
            if len(hits) >= seed.target_count * 3:
                break
        return hits

    async def _merge(self, hits: dict[str, dict], law: dict | None, country: str) -> None:
        code = (law or {}).get("code")
        if not code or not code.strip() or canonical(code) in hits:
            return
        resolved = law
        if not law.get("title") or law["title"].lower() == code.lower():
            try:
                resolved = next((l for l in await self.graph.search_laws(code, country)
                                 if (l.get("code") or "").lower() == code.lower()), law)
            except Exception as e:
                log.debug("metadata lookup failed for %s: %s", code, e)
        hits[canonical(code)] = resolved
