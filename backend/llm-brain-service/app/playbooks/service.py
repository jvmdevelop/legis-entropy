import json
import logging
import re
import uuid

from app.analysis.labor import last_number
from app.llm.client import Completer
from app.pipeline.quotes import QuoteChecker
from app.schemas import CamelModel, Playbook, PlaybookCheckResponse, PlaybookResult, PlaybookRule
from app.structure.classify import TYPES
from app.structure.parser import DocumentStructure

log = logging.getLogger(__name__)

KINDS = ("required", "forbidden", "max_days", "text")
_DAYS = re.compile(r"(?iu)(дн[еяий]|день|дней|суток)")
MAX_TEXT_RULES = 15
CLAUSE_CHARS = 350

TEMPLATES: list[Playbook] = [
    Playbook(id="template-supplier", name="Договоры с поставщиками и исполнителями", rules=[
        PlaybookRule(id="t1", kind="max_days", clause_type="PAYMENT", max_days=30,
                     text="Срок оплаты не должен превышать 30 дней"),
        PlaybookRule(id="t2", kind="required", clause_type="CONFIDENTIALITY",
                     text="В договоре обязательно должен быть пункт о конфиденциальности"),
        PlaybookRule(id="t3", kind="text",
                     text="Ответственность исполнителя не должна быть ограничена только прямыми убытками"),
        PlaybookRule(id="t4", kind="required", clause_type="DISPUTES",
                     text="Должен быть пункт о порядке разрешения споров"),
    ]),
    Playbook(id="template-lease", name="Аренда (для арендатора)", rules=[
        PlaybookRule(id="l1", kind="forbidden", clause_type="TERMINATION", party="Арендодатель", modality="RIGHT",
                     text="У арендодателя не должно быть права одностороннего расторжения"),
        PlaybookRule(id="l2", kind="text", text="Арендная плата не может увеличиваться в одностороннем порядке"),
        PlaybookRule(id="l3", kind="required", clause_type="LIABILITY",
                     text="Должна быть ответственность сторон за нарушение договора"),
    ]),
]


class PlaybookStore:
    def __init__(self, redis_url: str = ""):
        self._mem: dict[str, dict[str, dict]] = {}
        self._redis = None
        if redis_url:
            try:
                import redis.asyncio as redis
                self._redis = redis.from_url(redis_url, decode_responses=True)
            except Exception as e:
                log.warning("Redis unavailable for playbooks, using memory: %s", e)

    @staticmethod
    def _key(user_id: str) -> str:
        return f"brain:playbooks:{user_id}"

    async def _load(self, user_id: str) -> dict[str, dict]:
        if self._redis is not None:
            try:
                raw = await self._redis.hgetall(self._key(user_id))
                return {k: json.loads(v) for k, v in raw.items()}
            except Exception as e:
                log.warning("Redis read failed, using memory: %s", e)
        return dict(self._mem.get(user_id, {}))

    async def list(self, user_id: str) -> list[Playbook]:
        return [Playbook.model_validate(v) for v in (await self._load(user_id)).values()]

    async def get(self, user_id: str, playbook_id: str) -> Playbook | None:
        if playbook_id.startswith("template-"):
            return next((t for t in TEMPLATES if t.id == playbook_id), None)
        raw = (await self._load(user_id)).get(playbook_id)
        return Playbook.model_validate(raw) if raw else None

    async def save(self, user_id: str, playbook: Playbook) -> Playbook:
        if not playbook.id or playbook.id.startswith("template-"):
            playbook = playbook.model_copy(update={"id": uuid.uuid4().hex[:12]})
        rules = []
        for r in playbook.rules:
            kind = r.kind if r.kind in KINDS else "text"
            ctype = r.clause_type if r.clause_type in TYPES else None
            rules.append(r.model_copy(update={"id": r.id or uuid.uuid4().hex[:8], "kind": kind, "clause_type": ctype}))
        playbook = playbook.model_copy(update={"rules": rules})
        data = playbook.model_dump(by_alias=True)
        if self._redis is not None:
            try:
                await self._redis.hset(self._key(user_id), playbook.id, json.dumps(data, ensure_ascii=False))
                return playbook
            except Exception as e:
                log.warning("Redis write failed, using memory: %s", e)
        self._mem.setdefault(user_id, {})[playbook.id] = data
        return playbook

    async def delete(self, user_id: str, playbook_id: str) -> bool:
        if self._redis is not None:
            try:
                return bool(await self._redis.hdel(self._key(user_id), playbook_id))
            except Exception as e:
                log.warning("Redis delete failed: %s", e)
        return self._mem.get(user_id, {}).pop(playbook_id, None) is not None


class _Verdict(CamelModel):
    rule_id: str
    status: str
    clause: str | None = None
    quote: str | None = None
    explanation: str = ""


class _Verdicts(CamelModel):
    items: list[_Verdict] = []


SYSTEM = ("Ты — юрист, проверяешь договор на соответствие ВНУТРЕННИМ правилам организации (не закону). "
          "Для каждого правила реши: OK — договор соответствует; VIOLATED — нарушает; NOT_APPLICABLE — правило к "
          "договору не относится или условие отсутствует. clause — номер пункта, quote — точная цитата из пункта "
          "(дословно), explanation — одно предложение. Верни JSON {items:[{ruleId, status, clause, quote, explanation}]}.")


class PlaybookChecker:
    def __init__(self, llm: Completer | None = None):
        self.llm = llm

    def _structured(self, rule: PlaybookRule, st: DocumentStructure) -> PlaybookResult:
        base = dict(rule_id=rule.id, rule=rule.text or rule.kind)
        typed = [c for c in st.clauses if not rule.clause_type or any(t.code == rule.clause_type for t in c.types)]
        label = TYPES.get(rule.clause_type or "", "условие")
        if rule.kind == "required":
            if typed:
                c = typed[0]
                return PlaybookResult(**base, status="OK", clause=c.number, page=c.page,
                                      explanation=f"Есть пункт типа «{label}» (п. {c.number}).")
            return PlaybookResult(**base, status="VIOLATED", explanation=f"В договоре нет пункта типа «{label}».")
        if rule.kind == "forbidden":
            hits = [c for c in typed
                    if (not rule.party or (c.party or "").lower() == rule.party.lower())
                    and (not rule.modality or c.modality == rule.modality)]
            if hits:
                c = hits[0]
                return PlaybookResult(**base, status="VIOLATED", clause=c.number, page=c.page, quote=c.text[:200],
                                      explanation=f"Найдено запрещённое условие: {label}"
                                                  + (f", сторона — {c.party}" if c.party else "") + ".")
            return PlaybookResult(**base, status="OK", explanation="Запрещённое условие не найдено.")
        if rule.kind == "max_days":
            found = []
            for c in typed:
                for m in _DAYS.finditer(c.text):
                    value = last_number(c.text[max(0, m.start() - 40): m.start()])
                    if value is not None:
                        found.append((value, c))
            if not found:
                return PlaybookResult(**base, status="NOT_APPLICABLE",
                                      explanation=f"В пунктах «{label}» не найден срок в днях.")
            value, c = max(found, key=lambda x: x[0])
            if rule.max_days is not None and value > rule.max_days:
                return PlaybookResult(**base, status="VIOLATED", clause=c.number, page=c.page, quote=c.text[:200],
                                      explanation=f"Срок {value} дн. больше допустимых {rule.max_days} дн.")
            return PlaybookResult(**base, status="OK", clause=c.number, page=c.page,
                                  explanation=f"Максимальный срок в договоре — {value} дн.")
        return PlaybookResult(**base, status="UNKNOWN", explanation="Неизвестный тип правила.")

    async def _text_rules(self, rules: list[PlaybookRule], st: DocumentStructure, text: str) -> list[PlaybookResult]:
        if not rules:
            return []
        if self.llm is None:
            return [PlaybookResult(rule_id=r.id, rule=r.text, status="UNKNOWN", explanation="LLM недоступна.")
                    for r in rules]
        clauses = "\n".join(f"[{c.number}] {c.text[:CLAUSE_CHARS]}" for c in st.clauses) or text[:6000]
        body = "ПРАВИЛА:\n" + "\n".join(f"{r.id}: {r.text}" for r in rules) + "\n\nДОГОВОР:\n" + clauses
        try:
            out = await self.llm.structured(SYSTEM, body, _Verdicts)
        except Exception as e:
            log.warning("playbook LLM check failed: %s", e)
            out = None
        by_id = {v.rule_id: v for v in (out.items if out else [])}
        quotes = QuoteChecker(text)
        results = []
        for r in rules:
            v = by_id.get(r.id)
            if v is None:
                results.append(PlaybookResult(rule_id=r.id, rule=r.text, status="UNKNOWN",
                                              explanation="Проверка не дала ответа."))
                continue
            status = v.status.strip().upper() if v.status.strip().upper() in ("OK", "VIOLATED", "NOT_APPLICABLE") \
                else "UNKNOWN"
            quote = v.quote
            if quote and quotes.locate(quote) is None:
                quote = None
            c = st.clause(v.clause)
            results.append(PlaybookResult(rule_id=r.id, rule=r.text, status=status, clause=v.clause,
                                          page=c.page if c else None, quote=quote, explanation=v.explanation))
        return results

    async def check(self, playbook: Playbook, st: DocumentStructure, text: str) -> PlaybookCheckResponse:
        structured = [self._structured(r, st) for r in playbook.rules if r.kind != "text" and r.clause_type]
        text_rules = [r for r in playbook.rules if r.kind == "text" or not r.clause_type][:MAX_TEXT_RULES]
        results = structured + await self._text_rules(text_rules, st, text)
        order = {r.id: i for i, r in enumerate(playbook.rules)}
        results.sort(key=lambda x: order.get(x.rule_id, 0))
        n = lambda s: sum(x.status == s for x in results)  # noqa: E731
        summary = (f"Правил: {len(results)}. Соответствует — {n('OK')}, нарушено — {n('VIOLATED')}, "
                   f"не применимо — {n('NOT_APPLICABLE')}" + (f", не проверено — {n('UNKNOWN')}" if n("UNKNOWN") else "")
                   + ". Это внутренние правила организации, не требования закона.")
        return PlaybookCheckResponse(playbook_id=playbook.id, name=playbook.name, results=results, summary=summary)
