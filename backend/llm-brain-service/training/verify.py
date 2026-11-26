import argparse
import asyncio
import json
import logging
from pathlib import Path

from pydantic import BaseModel, RootModel

from app.config import Settings
from app.llm.client import LLMClient
from app.llm.tools import ToolRegistry
from app.pipeline.intent import TASK_LABELS
from training.generate import INTENT_CONTEXT, RISK_LEVELS

log = logging.getLogger("verify")
DATA = Path(__file__).parent / "data"
BATCH = 25
CONCURRENCY = 3


class _Verdict(BaseModel):
    i: int
    label: str


class _Verdicts(RootModel[list[_Verdict]]):
    pass


def _intent_prompt(rows: list[dict]) -> tuple[str, str]:
    opts = "\n".join(f"- {k}: {v} (контекст: {INTENT_CONTEXT[k]})" for k, v in TASK_LABELS.items())
    body = "\n".join(f"{i}. {r['text']}" for i, r in enumerate(rows))
    return (f"Ты размечаешь намерения пользователей юридического ассистента. Классы:\n{opts}\n"
            "Для каждого сообщения выбери ОДИН наиболее подходящий класс. Верни JSON-массив {i, label}."), body


def _risk_prompt(rows: list[dict]) -> tuple[str, str]:
    opts = "\n".join(f"- {k}: {v}" for k, v in RISK_LEVELS.items())
    body = "\n".join(f"{i}. [договор {r['contract_type']}] {r['text']}" for i, r in enumerate(rows))
    return (f"Ты юрист РК. Оцени риск каждого пункта договора для клиента. Классы:\n{opts}\n"
            "Верни JSON-массив {i, label}."), body


async def verify(task: str) -> None:
    path = DATA / f"{task}.jsonl"
    rows = [json.loads(line) for line in path.read_text().splitlines() if line.strip()]
    todo = [r for r in rows if "verified" not in r]
    llm = LLMClient.from_settings(Settings(), ToolRegistry())
    prompt_fn = _intent_prompt if task == "intent" else _risk_prompt
    sem = asyncio.Semaphore(CONCURRENCY)

    async def one(batch: list[dict]) -> None:
        async with sem:
            system, body = prompt_fn(batch)
            out = await llm.structured(system, body, _Verdicts)
            judged = {v.i: v.label.strip().upper() for v in (out.root if out else [])}
            for i, r in enumerate(batch):
                if i in judged:
                    r["judge"] = judged[i]
                    r["verified"] = judged[i] == r["label"]

    await asyncio.gather(*(one(todo[i:i + BATCH]) for i in range(0, len(todo), BATCH)))
    path.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows))
    done = [r for r in rows if "verified" in r]
    ok = sum(r["verified"] for r in done)
    log.info("%s: judged %d/%d, agreement %.1f%%", task, len(done), len(rows), 100 * ok / max(1, len(done)))
    by: dict[str, list[int]] = {}
    for r in done:
        by.setdefault(r["label"], []).append(int(r["verified"]))
    for k, v in sorted(by.items()):
        log.info("  %-22s %d/%d kept", k, sum(v), len(v))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("task", choices=["intent", "risk"])
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    for noisy in ("httpx", "openai", "app.llm"):
        logging.getLogger(noisy).setLevel(logging.WARNING)
    asyncio.run(verify(ap.parse_args().task))
