import math
import re
from collections import Counter

_WORD = re.compile(r"[\w\-]+", re.U)


def normalize(text: str | None) -> str:
    return re.sub(r"\s+", " ", (text or "").lower().replace("ё", "е")).strip()


def tokens(text: str | None) -> list[str]:
    return [t for t in _WORD.findall(normalize(text)) if len(t) > 1]


def stem(token: str, n: int = 6) -> str:
    return token[:n]


def stems(text: str | None) -> list[str]:
    return [stem(t) for t in tokens(text)]


def trigram_vector(text: str | None) -> Counter:
    t = f"  {normalize(text)}  "
    return Counter(t[i:i + 3] for i in range(len(t) - 2))


def cosine_counter(a: Counter, b: Counter) -> float:
    if not a or not b:
        return 0.0
    dot = sum(v * b.get(k, 0) for k, v in a.items())
    na = math.sqrt(sum(v * v for v in a.values()))
    nb = math.sqrt(sum(v * v for v in b.values()))
    return dot / (na * nb) if na and nb else 0.0


def cosine(a: list[float], b: list[float]) -> float:
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def truncate(text: str | None, limit: int, marker: str = "…") -> str:
    text = text or ""
    return text if len(text) <= limit else text[:limit].rstrip() + marker
