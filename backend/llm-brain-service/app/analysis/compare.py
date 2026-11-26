import logging
import re

from app.analysis.clauses import extract_clauses
from app.ml import text as T
from app.schemas import BrainResponse, CompareRequest
from app.llm.client import ChatModel
from app.clients.ports import DocumentPort
from app.clients.ports import GraphPort
from app.ml.embeddings import Embedder

log = logging.getLogger(__name__)

MAX_CHARS_PER_SIDE = 6000
MAX_SEGMENTS = 40
MAX_PAIRS = 12
UNAVAILABLE = "[Текст недоступен"

PROMPT = """Ты — юридический ассистент. Сравни два документа с точки зрения юриста, работающего с законодательством Казахстана.

=== ДОКУМЕНТ A: {lh} ===
{lt}

=== ДОКУМЕНТ B: {rh} ===
{rt}
{alignment}
Выдай структурированный анализ в формате Markdown со следующими разделами:

## Краткая суть
Одно-два предложения о том, что это за документы и как они соотносятся.

## Общие положения
Маркированный список пересекающихся норм, целей, областей регулирования.

## Существенные различия
Маркированный список различий с указанием стороны (A/B).

## Возможные противоречия
Если есть конфликты или коллизии норм — перечисли их. Если нет — напиши «не выявлено».

## Вывод для юриста
Краткий практический вывод: на что обратить внимание при работе с обоими документами.

Не выдумывай факты. Если текст одного из документов не предоставлен или обрезан, явно укажи это в выводе.
"""


def truncate(s: str | None) -> str:
    s = s or ""
    return s if len(s) <= MAX_CHARS_PER_SIDE else s[:MAX_CHARS_PER_SIDE] + "\n\n[...текст обрезан]"


def fallback(label: str | None, reason: str) -> str:
    return f"{UNAVAILABLE}{'' if label is None else f' для «{label}»'}: {reason}]"


def header_for(kind: str | None, label: str | None, id_: str | None) -> str:
    return ("закон " if (kind or "").upper() == "LAW" else "документ ") + str(label if label and label.strip() else id_)


def segments(text: str) -> list[tuple[str, str]]:
    cl = extract_clauses(text)
    if len(cl) >= 2:
        return [(c.id, c.text) for c in cl[:MAX_SEGMENTS]]
    parts = [p.strip() for p in re.split(r"\n\s*\n|\n", text) if len(p.strip()) >= 30]
    return [(str(i + 1), T.truncate(p, 400)) for i, p in enumerate(parts[:MAX_SEGMENTS])]


class ClauseAligner:
    def __init__(self, embedder: Embedder, threshold: float = 0.6):
        self.embedder, self.threshold = embedder, threshold

    async def align(self, left: str, right: str) -> list[tuple[str, str, str, str, float]]:
        ls, rs = segments(left), segments(right)
        if not ls or not rs or self.embedder is None:
            return []
        try:
            vecs = await self.embedder.embed([t for _, t in ls] + [t for _, t in rs])
        except Exception as e:
            log.warning("clause alignment embedding failed: %s", e)
            return []
        lv, rv = vecs[:len(ls)], vecs[len(ls):]
        pairs = []
        for (lid, lt), a in zip(ls, lv):
            j, sim = max(((j, T.cosine(a, b)) for j, b in enumerate(rv)), key=lambda x: x[1])
            if sim >= self.threshold:
                pairs.append((lid, lt, rs[j][0], rs[j][1], sim))
        return sorted(pairs, key=lambda p: -p[4])[:MAX_PAIRS]


def alignment_block(pairs) -> str:
    if not pairs:
        return ""
    lines = [f"- A[{lid}] «{T.truncate(lt, 200)}» ↔ B[{rid}] «{T.truncate(rt, 200)}» (сходство {s:.2f})"
             for lid, lt, rid, rt, s in pairs]
    return ("\n=== СОПОСТАВЛЕННЫЕ ПУНКТЫ (выравнивание по смысловой близости) ===\n" + "\n".join(lines) +
            "\nИспользуй эти пары при описании общих положений и различий; пункты без пары — уникальны для своей стороны.\n")


class CompareService:
    def __init__(self, documents: DocumentPort, graph: GraphPort, llm: ChatModel, aligner: ClauseAligner | None = None):
        self.documents, self.graph, self.llm, self.aligner = documents, graph, llm, aligner

    async def compare(self, req: CompareRequest) -> BrainResponse:
        lt = await self.resolve_text(req.left_kind, req.left_id, req.user_id, req.left_label)
        rt = await self.resolve_text(req.right_kind, req.right_id, req.user_id, req.right_label)
        lh = header_for(req.left_kind, req.left_label, req.left_id)
        rh = header_for(req.right_kind, req.right_label, req.right_id)
        pairs = []
        if self.aligner and not lt.startswith(UNAVAILABLE) and not rt.startswith(UNAVAILABLE):
            pairs = await self.aligner.align(lt, rt)
        prompt = PROMPT.format(lh=lh, lt=lt, rh=rh, rt=rt, alignment=alignment_block(pairs))
        log.info("Compare: A=%s (%d chars) vs B=%s (%d chars), aligned pairs=%d", lh, len(lt), rh, len(rt), len(pairs))
        return await self.llm.answer(prompt, [], use_tools=False)

    async def resolve_text(self, kind: str | None, id_: str | None, user_id: str | None, label: str | None) -> str:
        if not id_ or not id_.strip():
            return fallback(label, "ID не указан")
        k = (kind or "").upper()
        try:
            if k == "LAW":
                law = await self._lookup_law(id_)
                if law is None:
                    return fallback(label, "закон не найден в графе")
                parts = [p for p in (law.get("title"), law.get("summary")) if p]
                return truncate("\n\n".join(parts) if parts else (label or ""))
            if k == "DOCUMENT":
                text = await self.documents.text(id_, user_id)
                if not text or not text.strip():
                    return fallback(label, "текст документа пуст")
                return truncate(text)
            return fallback(label, f"неизвестный тип {kind}")
        except Exception as e:
            log.warning("Failed to load text for %s:%s: %s", kind, id_, e)
            return fallback(label, f"ошибка загрузки: {e}")

    async def _lookup_law(self, code: str) -> dict | None:
        try:
            g = await self.graph.law(code, "RK")
            if isinstance(g, dict) and isinstance(g.get("law"), dict):
                return g["law"]
        except Exception as e:
            log.warning("getLawWithRelationships failed for %s: %s — falling back to search", code, e)
        try:
            matches = await self.graph.search_laws(code, "RK")
            if matches:
                return matches[0]
        except Exception as e:
            log.warning("searchLaws fallback also failed for %s: %s", code, e)
        return None
