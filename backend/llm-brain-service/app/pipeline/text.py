import re
from functools import lru_cache


def normalize(value: str | None) -> str:
    return re.sub(r"\s+", " ", (value or "").lower().replace("ё", "е")).strip()


def contains_any(text: str, terms) -> bool:
    return any(t in text for t in terms)


@lru_cache(maxsize=512)
def _word(needle: str) -> re.Pattern:
    return re.compile(r"(?<![^\W_])" + re.escape(needle) + r"(?![^\W_])")


def contains_any_word(text: str, needles) -> bool:
    return any(_word(n).search(text) for n in needles)


def json_escape(s: str | None) -> str:
    return "" if s is None else s.replace("\\", "\\\\").replace('"', '\\"').replace("\n", " ")
