import json
import re
from dataclasses import dataclass

OPEN = "<tool_call"
_BLOCK = re.compile(r"<tool_call>(.*?)(?:</tool_call>|$)", re.S)
_FUNCTION = re.compile(r"<function=([\w.-]+)>(.*?)(?:</function>|$)", re.S)
_PARAM = re.compile(r"<parameter=([\w.-]+)>\s*(.*?)\s*</parameter>", re.S)


@dataclass(frozen=True)
class TextCall:
    name: str
    arguments: str


def _value(raw: str):
    try:
        return json.loads(raw)
    except ValueError:
        return raw


def parse(text: str, known: set[str] | None = None) -> tuple[list[TextCall], str]:
    calls: list[TextCall] = []
    for block in _BLOCK.findall(text or ""):
        body = block.strip()
        fn = _FUNCTION.search(body)
        if fn:
            args = {k: _value(v) for k, v in _PARAM.findall(fn.group(2))}
            calls.append(TextCall(fn.group(1), json.dumps(args, ensure_ascii=False)))
            continue
        try:
            obj = json.loads(body)
        except ValueError:
            continue
        if isinstance(obj, dict) and obj.get("name"):
            args = obj.get("arguments", obj.get("parameters", {}))
            calls.append(TextCall(obj["name"], args if isinstance(args, str) else json.dumps(args, ensure_ascii=False)))
    if known is not None:
        calls = [c for c in calls if c.name in known]
    rest = _BLOCK.sub("", text or "")
    rest = re.sub(r"</?tool_call>|</?function[^>]*>", "", rest).strip()
    return calls, rest


def has_call(text: str | None) -> bool:
    return bool(text) and OPEN in text


class StreamGuard:
    def __init__(self):
        self.pending = ""
        self.captured = ""
        self.capturing = False

    def feed(self, chunk: str) -> str:
        if self.capturing:
            self.captured += chunk
            return ""
        self.pending += chunk
        i = self.pending.find(OPEN)
        if i >= 0:
            out, self.captured, self.capturing = self.pending[:i], self.pending[i:], True
            self.pending = ""
            return out
        keep = 0
        for k in range(min(len(OPEN) - 1, len(self.pending)), 0, -1):
            if OPEN.startswith(self.pending[-k:]):
                keep = k
                break
        out = self.pending[: len(self.pending) - keep]
        self.pending = self.pending[len(self.pending) - keep:]
        return out

    def flush(self) -> str:
        out, self.pending = ("" if self.capturing else self.pending), ""
        return out
