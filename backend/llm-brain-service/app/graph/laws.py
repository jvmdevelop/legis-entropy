import json
import logging
import re
from typing import Any

Json = dict[str, Any]
log = logging.getLogger(__name__)


def default_country(country: str | None) -> str:
    return country if country and country.strip() else "RK"


def canonical(code: str) -> str:
    return re.sub(r"\s+", " ", code.strip()).upper()


def snippet(s: str | None, limit: int) -> str:
    s = (s or "").strip()
    return s if len(s) <= limit else s[:limit] + "…"


def law_text(law: Json) -> str:
    return " ".join(str(law.get(k) or "") for k in ("code", "title", "summary")).strip()


def article_text(a: Json) -> str:
    return f"{a.get('title') or ''} {a.get('body') or ''}".strip()


def is_low_signal(law: Json) -> bool:
    code = (law.get("code") or "").upper()
    if code.endswith(" РК") and len(code) <= 8:
        return False
    title = (law.get("title") or "").lower()
    if not title.strip():
        return False
    return (title.startswith(("о ратификации", "о денонсации", "о присоединении", "о подписании "))
            or "между республикой казахстан и" in title
            or "между правительством республики казахстан" in title)


def format_law_graph(resp: Json) -> str:
    law = resp.get("law") or {}
    out = [f"**{law.get('code')}: {law.get('title')}**\n\n"]
    if law.get("summary"):
        out.append(f"Описание: {law['summary']}\n\n")
    out.append(f"Статус: {law.get('status')}\nТип: {law.get('type')}\n")
    if law.get("adoptionDate"):
        out.append(f"Принят: {law['adoptionDate']}\n")
    if law.get("effectiveDate"):
        out.append(f"В силе: {law['effectiveDate']}\n")
    rel = resp.get("relationships")
    if rel is not None:
        out.append("\n**Связи с другими нормами:**\n")
        out.append(rel if isinstance(rel, str) else json.dumps(rel, ensure_ascii=False, default=str))
    return "".join(out)


def law_line(law: Json, with_summary: bool = False) -> str:
    s = f"- **{law.get('code')}**"
    if law.get("title"):
        s += f": {law['title']}"
    if with_summary and law.get("summary"):
        s += f" ({law['summary']})"
    return s


def parse_string_array(raw: str | None) -> list[str]:
    if not raw or not raw.strip():
        return []
    s = raw.strip()
    fence = re.search(r"```(?:json)?\s*(.*?)```", s, re.S)
    if fence:
        s = fence.group(1).strip()
    try:
        start = min(i for i in (s.find("["), s.find("{")) if i >= 0)
        node = json.loads(s[start:max(s.rfind("]"), s.rfind("}")) + 1])
        arr = node if isinstance(node, list) else node.get("codes", [])
    except (ValueError, AttributeError):
        arr = re.findall(r'"([^"]+)"', s[s.find("["):s.rfind("]") + 1]) if "[" in s else []
    seen, out = set(), []
    for v in arr:
        if isinstance(v, str) and v.strip() and v.strip().upper() not in seen:
            seen.add(v.strip().upper())
            out.append(v.strip())
    return out


async def rank(ranker, query: str, items: list, text_of, top_k: int) -> list[tuple[Any, float]]:
    if not items:
        return []
    try:
        ranked = await ranker.rank(query, items, text_of, top_k=top_k, min_score=float("-inf"))
        return [(r.item, max(0.0, min(1.0, float(r.score)))) for r in ranked]
    except Exception as e:
        log.debug("ranking failed, keeping source order: %s", e)
        return [(x, 0.0) for x in items[:top_k]]
