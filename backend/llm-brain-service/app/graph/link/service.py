import logging
import re
from dataclasses import dataclass, field

from app.clients.ports import GraphPort
from app.graph.laws import default_country, is_low_signal, law_text, parse_string_array, rank
from app.graph.link.base import LinkableSubject, SubjectLinker
from app.ml.hybrid import HybridRanker
from app.llm.client import Completer

log = logging.getLogger(__name__)

QUERY_PROMPT = """Ты — правовой аналитик Республики Казахстан. На основе описания ситуации ниже \
сформируй 3–5 поисковых запросов для базы нормативных актов РК.

Правила:
- Каждый запрос — короткий правовой термин, название кодекса или тема (например: "ГПК", "отложение судебного заседания", "уголовный кодекс", "трудовой договор").
- Если ситуация процессуальная (ходатайство, перенос, отвод, иск) — первым запросом ставь соответствующий кодекс ("ГПК РК", "ГК РК", "УК РК", "КоАП РК" и т.д.).
- Не добавляй лишних слов — только JSON-массив строк.

Ситуация: {text}

Ответ (только JSON):"""

MARKERS = [
    ("опьянени", "состояние опьянения"), ("алкогол", "алкогольное опьянение"), ("нетрезв", "нетрезвое состояние"),
    ("халатност", "халатность"), ("неисполнен", "неисполнение обязанностей"), ("безопасност", "безопасность"),
    ("телесн", "телесные повреждения"), ("вред здоров", "причинение вреда здоровью"), ("ушиб", "телесные повреждения"),
    ("травм", "телесные повреждения"), ("увеч", "телесные повреждения"), ("несовершеннолет", "несовершеннолетний"),
    ("ребен", "защита ребенка"), ("малолет", "малолетний"), ("общественн", "общественный порядок"),
    ("мелк", "мелкое хулиганство"), ("хулиганств", "хулиганство"), ("угрожа", "угроза"),
    ("вымогат", "вымогательство"), ("мошен", "мошенничество"), ("оскорб", "оскорбление"), ("клевет", "клевета"),
    ("ходатайств", "ГПК отложение заседания"), ("отложен", "отложение судебного заседания"),
    ("перенос заседан", "отложение судебного заседания"), ("суд заседан", "ГПК судебное заседание"),
    ("подсудност", "подсудность"), ("арбитраж", "арбитраж"), ("исковое заявлен", "ГПК исковое заявление"),
    ("смэс", "ГПК экономический суд"), ("потребител", "защита прав потребителей"), ("возмещен", "возмещение вреда"),
    ("моральн", "моральный вред"), ("неустойк", "неустойка"), ("трудово", "трудовой договор"), ("работник", "труд"),
    ("командировк", "трудовой кодекс командировка"), ("оказани", "оказание услуг"), ("поставк", "поставка товаров"),
    ("аренд", "аренда"), ("подряд", "подряд"), ("купли-продаж", "купля-продажа"), ("займ", "займ"),
    ("кредит", "кредит"), ("налог", "налог"), ("акционерн", "акционерное общество"), ("товарищество", "товарищество"),
    ("страхов", "страхование"), ("наслед", "наследование"), ("брак", "семейное право"), ("уголовн", "уголовный кодекс"),
    ("гражданск", "гражданский кодекс"), ("исковая давность", "исковая давность"), ("договор", "договор"),
    ("штраф", "штраф"), ("пени", "пени"), ("спор", "разрешение споров"), ("претензи", "претензия"),
    ("конфиденциальн", "конфиденциальность"), ("персональн", "персональные данные"),
    ("интеллектуальн", "интеллектуальная собственность"),
]


def keyword_queries(text: str, markers=MARKERS, title_line: bool = True) -> list[str]:
    t = text.lower()
    queries: dict[str, None] = {}
    for stem, q in markers:
        if stem in t:
            queries[q] = None
        if len(queries) >= 12:
            break
    if title_line:
        for ln in re.split(r"[\r\n]+", text, maxsplit=4):
            s = ln.strip()
            if 8 <= len(s) <= 80 and not re.search(r"\d", s):
                queries[s] = None
                break
    return list(queries) or ["ГК РК"]


@dataclass
class LinkResult:
    linked: int
    report: str
    law_codes: list[str] = field(default_factory=list)


class SubjectAwareLinkService:
    def __init__(self, graph: GraphPort, llm: Completer, ranker: HybridRanker):
        self.graph, self.llm, self.ranker = graph, llm, ranker

    async def search_queries(self, text: str) -> list[str]:
        try:
            parsed = parse_string_array(await self.llm.complete("", QUERY_PROMPT.format(text=text[:700])))
            if parsed:
                log.info("LLM generated %d legal search queries: %s", len(parsed), parsed)
                return parsed
        except Exception as e:
            log.debug("LLM smart query generation failed, falling back to keyword heuristics: %s", e)
        return keyword_queries(text)

    async def rank_laws(self, text: str, laws: list[dict], top_k: int) -> list[dict]:
        return [law for law, _ in await rank(self.ranker, text[:2000], laws, law_text, top_k)]

    async def link_subject_to_laws(self, linker: SubjectLinker, subject: LinkableSubject, graph_id: str,
                                   limit: int, country: str | None) -> LinkResult:
        limit = max(1, min(limit, 10))
        ctry = default_country(country)
        label = linker.display_name
        log.info("SubjectAwareLinkService: linking %s %s to laws in graph=%s, limit=%d", label, subject.id,
                 graph_id, limit)
        text = await linker.fetch_text(subject, graph_id)
        if not text or not text.strip():
            return LinkResult(0, f"{label} найден, но текст ещё не извлечён — попробуйте ещё раз через несколько секунд.")

        hits: dict[str, dict] = {}
        for q in await self.search_queries(text):
            try:
                for law in await self.graph.search_laws(q, ctry):
                    if law.get("code") and not is_low_signal(law):
                        hits.setdefault(law["code"], law)
            except Exception as e:
                log.debug("searchLaws('%s') failed: %s", q, e)
            if len(hits) >= limit * 3:
                break
        if not hits:
            return LinkResult(0, f"В базе не найдено законов, релевантных {linker.display_accusative}. Связи не созданы.")

        chosen = await self.rank_laws(text, list(hits.values()), limit)
        header = f"**{label}" + (f" «{subject.label}»" if subject.label and subject.label.strip() else "")
        lines, codes = [header + "** связан со следующими нормами:"], []
        for law in chosen:
            try:
                await self.graph.add_law(graph_id, law["code"], ctry)
                added = True
            except Exception as e:
                log.error("add law %s failed: %s", law["code"], e)
                added = False
            if await linker.link_to_law(graph_id, subject, law["code"], ctry) and added:
                codes.append(law["code"])
                lines.append(f"- **{law['code']}**" + (f": {law['title']}" if law.get("title") else ""))
        if not codes:
            return LinkResult(0, "Найдены релевантные законы, но ни один не удалось добавить в граф "
                                 "(см. логи graphs-service).", codes)
        lines.append("\n_Связи установлены с применением семантического анализа правового контекста "
                     "(BGE-M3 + реранкер). Источник: LLM_INFERENCE (semantic)._")
        return LinkResult(len(codes), "\n".join(lines), codes)
