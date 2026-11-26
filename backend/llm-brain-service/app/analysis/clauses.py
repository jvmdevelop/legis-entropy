import logging
import re
from dataclasses import dataclass

from pydantic import BaseModel, field_validator
from app.llm.client import Completer

log = logging.getLogger(__name__)

MAX_CLAUSE_TEXT = 400
MAX_CLAUSES = 50
_CLAUSE_START = re.compile(r"(?im)^\s*(?:п\.?\s*|пункт\s+)?(\d+(?:\.\d+)*)[.)]\s+(\S.{4,})")


@dataclass(frozen=True)
class Clause:
    id: str
    text: str


def _cut(body: str) -> str:
    body = body.strip()
    return body if len(body) <= MAX_CLAUSE_TEXT else body[:MAX_CLAUSE_TEXT].strip() + "…"


def extract_clauses(text: str | None) -> list[Clause]:
    if not text or not text.strip():
        return []
    starts = [(m.start(), m.group(1)) for m in _CLAUSE_START.finditer(text)][:MAX_CLAUSES]
    return [Clause(cid, _cut(text[pos: starts[i + 1][0] if i + 1 < len(starts) else len(text)]))
            for i, (pos, cid) in enumerate(starts)]


class _Seg(BaseModel):
    id: str
    text: str

    @field_validator("id", mode="before")
    @classmethod
    def _str(cls, v):
        return str(v)


class _Segmentation(BaseModel):
    clauses: list[_Seg]


class ClauseSegmenter:
    SYSTEM = ("Ты — модуль сегментации договоров. Раздели текст договора на отдельные смысловые пункты "
              "(условия). Сохрани исходную нумерацию пунктов, если она есть, иначе нумеруй 1, 2, 3… "
              "Текст пункта копируй дословно из договора, не перефразируй.")

    def __init__(self, llm: Completer | None = None, llm_input_limit: int = 15000):
        self.llm = llm
        self.limit = llm_input_limit

    async def segment(self, text: str) -> list[Clause]:
        clauses = extract_clauses(text)
        if len(clauses) >= 2 or self.llm is None or not text.strip():
            return clauses
        out = await self.llm.structured(self.SYSTEM, text[:self.limit], _Segmentation)
        if out is None:
            log.info("LLM clause segmentation unavailable, regex result kept (%d clauses)", len(clauses))
            return clauses
        segs = [Clause(s.id.strip() or str(i + 1), _cut(s.text)) for i, s in enumerate(out.clauses) if s.text.strip()]
        return segs[:MAX_CLAUSES] if len(segs) > len(clauses) else clauses
