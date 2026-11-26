import re
from dataclasses import dataclass, field

_SECTION = re.compile(r"(?m)^[ \t]*(?:(?:раздел|глава)\s+)?(\d+)\.?[ \t]+([^\n]{2,140})$", re.I)
_CLAUSE = re.compile(r"(?m)^[ \t]*(\d+(?:\.\d+)+)\.?[ \t]+")


@dataclass
class Page:
    page: int
    start: int
    end: int


@dataclass
class Section:
    number: str
    title: str
    start: int
    end: int
    page: int


@dataclass
class Paragraph:
    start: int
    end: int
    page: int


@dataclass
class ClauseType:
    code: str
    label: str
    source: str = "rule"


@dataclass
class Clause:
    number: str
    text: str
    start: int
    end: int
    page: int
    section: str | None = None
    paragraphs: list[Paragraph] = field(default_factory=list)
    types: list[ClauseType] = field(default_factory=list)
    party: str | None = None
    modality: str | None = None


@dataclass
class DocumentStructure:
    pages: list[Page]
    sections: list[Section]
    clauses: list[Clause]

    def clause(self, number: str | None) -> Clause | None:
        return next((c for c in self.clauses if c.number == number), None) if number else None

    def page_of(self, offset: int) -> int:
        return page_at(self.pages, offset)


def page_at(pages: list[Page], offset: int) -> int:
    for p in pages:
        if p.start <= offset < p.end:
            return p.page
    return pages[-1].page if pages and offset >= pages[-1].end else 1


def _is_section(title: str) -> bool:
    letters = [ch for ch in title if ch.isalpha()]
    return bool(letters) and sum(ch.isupper() for ch in letters) / len(letters) > 0.7


def parse(text: str, pages: list[Page] | None = None) -> DocumentStructure:
    text = text or ""
    pages = pages or [Page(1, 0, len(text))]
    sections: list[Section] = []
    for m in _SECTION.finditer(text):
        if "." in m.group(1) or not _is_section(m.group(2)):
            continue
        sections.append(Section(m.group(1), m.group(2).strip(), m.start(), len(text), page_at(pages, m.start())))
    for a, b in zip(sections, sections[1:]):
        a.end = b.start

    starts = [(m.start(), m.group(1)) for m in _CLAUSE.finditer(text)]
    bounds = sorted({s for s, _ in starts} | {s.start for s in sections} | {len(text)})
    clauses: list[Clause] = []
    for start, number in starts:
        end = next(b for b in bounds if b > start)
        body = text[start:end].rstrip()
        section = next((s.number for s in reversed(sections) if s.start <= start), None)
        paragraphs = []
        pos = start
        for line in body.split("\n"):
            if line.strip():
                paragraphs.append(Paragraph(pos, pos + len(line), page_at(pages, pos)))
            pos += len(line) + 1
        clauses.append(Clause(number, body, start, start + len(body), page_at(pages, start), section, paragraphs))
    return DocumentStructure(pages, sections, clauses)
