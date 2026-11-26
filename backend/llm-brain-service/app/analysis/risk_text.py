import re

_NUMBERING = re.compile(r"^\s*(?:п\.\s*)?\d+(?:\.\d+)*\.?\)?\s*")
_HEADING = re.compile(r"^[А-ЯЁA-Z][^.!?:]{2,45}\.\s+(?=[А-ЯЁA-Z«\"])")
_HEADING_MAX_WORDS = 5


def _strip_heading(text: str) -> str:
    m = _HEADING.match(text)
    return text[m.end():] if m and len(m.group(0).split()) <= _HEADING_MAX_WORDS else text


def model_input(contract_type: str, clause_text: str) -> str:
    text = _NUMBERING.sub("", clause_text or "", count=1).strip()
    return f"договор {contract_type}: {_strip_heading(text)}"
