import argparse
import asyncio
import json
import logging
import random
from pathlib import Path

from pydantic import BaseModel, RootModel

from app.analysis.contract_types import LABELS as CONTRACT_LABELS
from app.config import Settings
from app.llm.client import LLMClient
from app.llm.tools import ToolRegistry
from app.pipeline.intent import TASK_LABELS
from app.pipeline.profession import LABELS as PROFESSION_LABELS

log = logging.getLogger("generate")
DATA = Path(__file__).parent / "data"
BATCH = 20
CONCURRENCY = 3

STYLES = [
    "разговорный, без заглавных букв, с опечатками",
    "официально-деловой, полными предложениями",
    "очень короткий (3-7 слов)",
    "длинный, с описанием жизненной ситуации и деталями",
    "эмоциональный, пользователь взволнован",
    "смесь русского и казахских слов, как пишут в Казахстане",
    "вопрос юриста-профессионала коллеге, с терминами",
    "без вопросительного знака, в повелительной форме",
]

INTENT_CONTEXT = {
    "DOCUMENT_AUDIT": "у пользователя прикреплён документ (договор/соглашение/иск)",
    "DOCUMENT_QA": "у пользователя прикреплён документ, он спрашивает о его содержании",
    "SUMMARY": "у пользователя прикреплён документ или длинный текст",
    "LAW_SEARCH": "документа нет, пользователь ищет нормы права",
    "GENERAL_LEGAL_ADVICE": "документа нет, жизненная ситуация, просьба совета",
    "DOCUMENT_DRAFT": "пользователь просит составить документ",
    "GRAPH_ADD_LAWS": "пользователь работает в визуальном графе законов (GraphView)",
    "GRAPH_LINK_DOCUMENT": "в графе есть документ/ситуация/голосовое сообщение пользователя",
    "GRAPH_DEEP_ANALYSIS": "пользователь в графе, просит максимально глубокий анализ",
}
CONFUSERS = {
    "DOCUMENT_AUDIT": "не используй слово 'проверь' в половине примеров",
    "LAW_SEARCH": "часть примеров — вопросы 'какая ответственность', 'что грозит', без слова 'закон'",
    "GENERAL_LEGAL_ADVICE": "часть примеров упоминает закон, но суть — просьба совета",
    "DOCUMENT_DRAFT": "часть примеров без слов 'составь/напиши', например 'нужна претензия к продавцу'",
}

RISK_LEVELS = {
    "VIOLATION": "прямо нарушает императивную норму законодательства РК (ГК РК, Трудовой кодекс РК 414-V и др.)",
    "WARNING": "законен, но невыгоден или рискован для клиента (односторонние права, высокие штрафы, размытые сроки)",
    "COMPLIANT": "обычный корректный пункт, соответствует закону",
}


class _Texts(RootModel[list[str]]):
    pass


class _Clause(BaseModel):
    text: str
    law_citation: str | None = None
    reason: str | None = None


class _Clauses(RootModel[list[_Clause]]):
    pass


MAX_IDLE_ROUNDS = 4


class _Progress:
    def __init__(self, have: dict):
        self.have, self.idle, self.last = have, 0, sum(have.values())

    def ok(self) -> bool:
        total = sum(self.have.values())
        self.idle = 0 if total > self.last else self.idle + 1
        self.last = total
        if self.idle >= MAX_IDLE_ROUNDS:
            log.error("no progress for %d rounds, stopping", self.idle)
        return self.idle < MAX_IDLE_ROUNDS


def _load(path: Path) -> list[dict]:
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def _append(path: Path, rows: list[dict]) -> None:
    with path.open("a") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


async def _batch_texts(llm: LLMClient, system: str, prompt: str) -> list[str]:
    out = await llm.structured(system, prompt, _Texts)
    return [t.strip() for t in (out.root if out else []) if isinstance(t, str) and len(t.strip()) >= 3]


async def gen_intent(llm: LLMClient, per_class: int, path: Path) -> None:
    rows = _load(path)
    have = {k: sum(r["label"] == k for r in rows) for k in TASK_LABELS}
    seen = {r["text"].lower() for r in rows}
    sem = asyncio.Semaphore(CONCURRENCY)

    async def one(label: str) -> None:
        async with sem:
            style = random.choice(STYLES)
            prompt = (f"Сгенерируй {BATCH} РАЗНЫХ сообщений пользователя юридическому ИИ-ассистенту (Казахстан/РФ).\n"
                      f"Намерение: {label} — {TASK_LABELS[label]}\nКонтекст: {INTENT_CONTEXT[label]}\n"
                      f"Стиль: {style}\n{CONFUSERS.get(label, '')}\n"
                      "Разные отрасли права (труд, семья, жильё, бизнес, налоги, уголовное, административное). "
                      "Верни JSON-массив строк.")
            texts = await _batch_texts(llm, "Ты генерируешь обучающие данные для классификатора намерений.", prompt)
            new = [t for t in texts if t.lower() not in seen]
            seen.update(t.lower() for t in new)
            _append(path, [{"text": t, "label": label, "style": style} for t in new])
            have[label] += len(new)
            log.info("intent %s +%d (%d/%d)", label, len(new), have[label], per_class)

    pr = _Progress(have)
    while any(have[k] < per_class for k in have) and pr.ok():
        todo = [k for k in have if have[k] < per_class]
        await asyncio.gather(*(one(k) for k in todo for _ in range(min(3, -(-(per_class - have[k]) // BATCH)))))


async def gen_profession(llm: LLMClient, per_class: int, path: Path) -> None:
    rows = _load(path)
    labels = list(PROFESSION_LABELS)
    have = {k: sum(k in r["labels"] for r in rows) for k in labels}
    seen = {r["text"].lower() for r in rows}
    sem = asyncio.Semaphore(CONCURRENCY)

    async def one(label: str) -> None:
        async with sem:
            style = random.choice(STYLES)
            other = random.choice([x for x in labels if x not in (label, "GENERAL_LAWYER")])
            mixed = label != "GENERAL_LAWYER" and random.random() < 0.25
            topic = (f"{PROFESSION_LABELS[label]} И одновременно {PROFESSION_LABELS[other]}" if mixed
                     else PROFESSION_LABELS[label])
            prompt = (f"Сгенерируй {BATCH} РАЗНЫХ сообщений пользователя юридическому ассистенту (Казахстан).\n"
                      f"Тема: {topic}\nСтиль: {style}\n"
                      "Не называй отрасль права прямо в половине примеров — только ситуацию. Верни JSON-массив строк.")
            texts = await _batch_texts(llm, "Ты генерируешь обучающие данные для классификатора отрасли права.", prompt)
            new = [t for t in texts if t.lower() not in seen]
            seen.update(t.lower() for t in new)
            lbl = [label, other] if mixed else [label]
            _append(path, [{"text": t, "labels": lbl, "style": style} for t in new])
            for x in lbl:
                have[x] = have.get(x, 0) + len(new)
            log.info("profession %s +%d (%d/%d)", "+".join(lbl), len(new), have[label], per_class)

    pr = _Progress(have)
    while any(have[k] < per_class for k in labels) and pr.ok():
        todo = [k for k in labels if have[k] < per_class]
        await asyncio.gather(*(one(k) for k in todo for _ in range(min(3, -(-(per_class - have[k]) // BATCH)))))


CLAUSE_FORMS = [
    "короткие, одно предложение, с конкретными цифрами (суммы в тенге, сроки, даты, проценты)",
    "1-3 предложения, как в реальных договорах",
    "простым языком, как пишут в шаблонах из интернета",
    "одно длинное предложение с оговорками",
]


async def gen_risk(llm: LLMClient, per_class: int, path: Path) -> None:
    rows = _load(path)
    types = [t for t in CONTRACT_LABELS if t != "иной"]
    keys = [(t, lv) for t in types for lv in RISK_LEVELS]
    have = {k: sum((r["contract_type"], r["label"]) == k for r in rows) for k in keys}
    seen = {r["text"].lower() for r in rows}
    sem = asyncio.Semaphore(CONCURRENCY)

    async def one(key) -> None:
        ctype, level = key
        async with sem:
            prompt = (f"Сгенерируй {BATCH} РАЗНЫХ пунктов договора {ctype} (Республика Казахстан). "
                      f"Форма: {random.choice(CLAUSE_FORMS)}. Без нумерации и без заголовков пунктов.\n"
                      f"Класс риска: {level} — {RISK_LEVELS[level]}.\n"
                      "Для VIOLATION/WARNING укажи law_citation (статья и закон) и reason (кратко почему). "
                      "Для COMPLIANT law_citation и reason = null. Разные разделы договора (предмет, оплата, сроки, "
                      "ответственность, расторжение, конфиденциальность, форс-мажор). Верни JSON-массив объектов.")
            out = await llm.structured("Ты юрист РК и генерируешь размеченные пункты договоров.", prompt, _Clauses)
            new = [c for c in (out.root if out else []) if len(c.text.strip()) > 15 and c.text.lower() not in seen]
            seen.update(c.text.lower() for c in new)
            _append(path, [{"text": c.text.strip(), "label": level, "contract_type": ctype,
                            "law_citation": c.law_citation, "reason": c.reason} for c in new])
            have[key] += len(new)
            log.info("risk %s/%s +%d (%d/%d)", ctype, level, len(new), have[key], per_class)

    pr = _Progress(have)
    while any(have[k] < per_class for k in keys) and pr.ok():
        todo = [k for k in keys if have[k] < per_class]
        await asyncio.gather(*(one(k) for k in todo))


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("task", choices=["intent", "profession", "risk"])
    ap.add_argument("--per-class", type=int, default=0)
    a = ap.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s")
    for noisy in ("httpx", "openai", "app.llm"):
        logging.getLogger(noisy).setLevel(logging.WARNING)
    DATA.mkdir(exist_ok=True)
    llm = LLMClient.from_settings(Settings(), ToolRegistry())
    n = a.per_class or {"intent": 260, "profession": 220, "risk": 120}[a.task]
    fn = {"intent": gen_intent, "profession": gen_profession, "risk": gen_risk}[a.task]
    await fn(llm, n, DATA / f"{a.task}.jsonl")


if __name__ == "__main__":
    asyncio.run(main())
