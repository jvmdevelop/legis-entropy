import html
import re

TELEGRAM_LIMIT = 4096
_SAFE_LIMIT = 3900

_BOLD = re.compile(r"\*\*(.+?)\*\*")
_ITALIC = re.compile(r"(?<![\w*])_(?!_)(.+?)(?<!_)_(?![\w*])")
_CODE = re.compile(r"`([^`]+)`")
_HEADING = re.compile(r"^#{1,6}\s+(.*)$")
_BULLET = re.compile(r"^(\s*)[-*]\s+(.*)$")
_QUOTE = re.compile(r"^>\s?(.*)$")
_RULE = re.compile(r"^\s*(-{3,}|\*{3,})\s*$")


def _inline(text: str) -> str:
    text = html.escape(text, quote=False)
    text = _CODE.sub(r"<code>\1</code>", text)
    text = _BOLD.sub(r"<b>\1</b>", text)
    text = _ITALIC.sub(r"<i>\1</i>", text)
    return text


def to_telegram_html(markdown: str) -> str:
    out = []
    for raw in markdown.replace("\r\n", "\n").split("\n"):
        line = raw.rstrip()
        if _RULE.match(line):
            out.append("")
        elif m := _HEADING.match(line):
            out.append(f"<b>{_inline(m.group(1))}</b>")
        elif m := _QUOTE.match(line):
            out.append(f"<blockquote>{_inline(m.group(1))}</blockquote>" if m.group(1) else "")
        elif m := _BULLET.match(line):
            indent = "  " if m.group(1) else ""
            out.append(f"{indent}• {_inline(m.group(2))}")
        else:
            out.append(_inline(line))
    text = "\n".join(out)
    return re.sub(r"\n{3,}", "\n\n", text).strip()


def split_message(text: str, limit: int = _SAFE_LIMIT) -> list[str]:
    if len(text) <= limit:
        return [text] if text else []
    parts: list[str] = []
    current = ""
    for line in text.split("\n"):
        while len(line) > limit:
            if current:
                parts.append(current)
                current = ""
            parts.append(line[:limit])
            line = line[limit:]
        candidate = f"{current}\n{line}" if current else line
        if len(candidate) > limit:
            parts.append(current)
            current = line
        else:
            current = candidate
    if current:
        parts.append(current)
    return [p.strip() for p in parts if p.strip()]
