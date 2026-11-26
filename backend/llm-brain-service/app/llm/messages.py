from dataclasses import dataclass, field

from app.schemas import HistoryEntry

MAX_HISTORY_MESSAGES = 8
MAX_HISTORY_CHARS = 6000
MAX_ENTRY_CHARS = 2000


def trim_history(history: list[HistoryEntry] | None, max_messages: int = MAX_HISTORY_MESSAGES,
                 max_chars: int = MAX_HISTORY_CHARS, max_entry: int = MAX_ENTRY_CHARS) -> list[dict]:
    out: list[dict] = []
    total = 0
    for h in reversed(history or []):
        role = (h.role or "").lower()
        if role not in ("user", "assistant") or not h.content:
            continue
        text = h.content if len(h.content) <= max_entry else h.content[:max_entry] + "…"
        if len(out) >= max_messages or total + len(text) > max_chars:
            break
        out.append({"role": role, "content": text})
        total += len(text)
    return list(reversed(out))


@dataclass
class MessageBuilder:
    system_prompt: str = ""
    few_shot: list[tuple[str, str]] = field(default_factory=list)
    few_shot_first_turn_only: bool = True

    def chat(self, user_prompt: str, history: list[HistoryEntry] | None) -> list[dict]:
        msgs: list[dict] = [{"role": "system", "content": self.system_prompt}] if self.system_prompt else []
        past = trim_history(history)
        if not past or not self.few_shot_first_turn_only:
            for u, a in self.few_shot:
                msgs += [{"role": "user", "content": u}, {"role": "assistant", "content": a}]
        msgs += past
        msgs.append({"role": "user", "content": user_prompt})
        return msgs

    @staticmethod
    def plain(system_prompt: str, user_prompt: str) -> list[dict]:
        return [{"role": "system", "content": system_prompt}, {"role": "user", "content": user_prompt}]
