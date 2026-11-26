import re
from dataclasses import dataclass, field
from difflib import SequenceMatcher

QUOTE = re.compile(r"«([^«»]{8,800})»|“([^“”]{8,800})”|\"([^\"\n]{8,800})\"")
CLAUSE_REF = re.compile(r"(?iu)(?:п\.|пункт[а-я]*|пп\.)\s*(\d+(?:\.\d+)+)\s*[:,—–-]?\s*$")
CLAUSE_LINE = re.compile(r"(?m)^[ \t]*(\d+(?:\.\d+)+)\.?\s")
TITLE_LIKE = re.compile(r"(?iu)^\s*об?\s")
MIN_WORDS = 4
FLAG_WORDS = 6
FUZZY = 0.9
NOT_FOUND = " _(цитата не найдена в документе)_"
_SPACE = re.compile(r"\s+")
_TRANS = str.maketrans({"ё": "е", "Ё": "е", "–": "-", "—": "-", "«": '"', "»": '"', "“": '"', "”": '"', " ": " "})


@dataclass
class QuoteReport:
    exact: int = 0
    fixed: int = 0
    not_found: int = 0
    clause_fixed: int = 0
    details: list[str] = field(default_factory=list)


def normalize(text: str) -> tuple[str, list[int]]:
    out, index, prev_space = [], [], False
    for i, ch in enumerate(text.translate(_TRANS)):
        if ch.isspace():
            if prev_space or not out:
                continue
            out.append(" ")
            index.append(i)
            prev_space = True
            continue
        out.append(ch.lower())
        index.append(i)
        prev_space = False
    return "".join(out), index


def clause_at(text: str, pos: int) -> str | None:
    found = None
    for m in CLAUSE_LINE.finditer(text):
        if m.start() > pos:
            break
        found = m.group(1)
    return found


class QuoteChecker:
    def __init__(self, document: str):
        self.doc = document or ""
        self.norm, self.index = normalize(self.doc)

    def locate(self, quote: str) -> tuple[int, int, float] | None:
        q, _ = normalize(quote.strip())
        if not q:
            return None
        i = self.norm.find(q)
        if i >= 0:
            return self._span(i, i + len(q)) + (1.0,)
        m = SequenceMatcher(None, q, self.norm, autojunk=False).find_longest_match(0, len(q), 0, len(self.norm))
        if m.size < max(8, len(q) // 4):
            return None
        start = max(0, m.b - m.a)
        best = None
        for delta in range(-len(q) // 5, len(q) // 5 + 1, max(1, len(q) // 40)):
            a, b = max(0, start + min(0, delta)), min(len(self.norm), start + len(q) + delta)
            ratio = SequenceMatcher(None, q, self.norm[a:b], autojunk=False).ratio()
            if best is None or ratio > best[2]:
                best = (a, b, ratio)
        if best is None or best[2] < FUZZY:
            return None
        return self._span(best[0], best[1]) + (best[2],)

    def _span(self, a: int, b: int) -> tuple[int, int]:
        start = self.index[a]
        end = self.index[min(b, len(self.index)) - 1] + 1
        while start > 0 and self.doc[start - 1].isalnum():
            start -= 1
        while end < len(self.doc) and self.doc[end].isalnum():
            end += 1
        return start, end

    def check(self, answer: str) -> tuple[str, QuoteReport]:
        report = QuoteReport()
        if not self.doc.strip() or not answer:
            return answer, report
        out, last = [], 0
        for m in QUOTE.finditer(answer):
            inner = next(g for g in m.groups() if g is not None)
            words = len(inner.split())
            if words < MIN_WORDS or TITLE_LIKE.match(inner):
                continue
            hit = self.locate(inner)
            prefix = answer[last:m.start()]
            if hit is None:
                out.append(prefix + m.group(0) + (NOT_FOUND if words >= FLAG_WORDS else ""))
                if words >= FLAG_WORDS:
                    report.not_found += 1
                    report.details.append(f"не найдено: {inner[:60]}")
                last = m.end()
                continue
            start, end, ratio = hit
            exact_text = self.doc[start:end].strip()
            quote_text = m.group(0) if ratio == 1.0 else m.group(0)[0] + exact_text + m.group(0)[-1]
            report.exact += ratio == 1.0
            if ratio < 1.0:
                report.fixed += 1
                report.details.append(f"исправлено: {inner[:40]} -> {exact_text[:40]}")
            ref = CLAUSE_REF.search(prefix[-40:])
            real = clause_at(self.doc, start)
            if ref and real and ref.group(1) != real:
                cut = len(prefix) - len(prefix[-40:]) + ref.start(1)
                prefix = prefix[:cut] + real + prefix[cut + len(ref.group(1)):]
                report.clause_fixed += 1
                report.details.append(f"пункт {ref.group(1)} -> {real}")
            out.append(prefix + quote_text)
            last = m.end()
        out.append(answer[last:])
        return "".join(out), report
