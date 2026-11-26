import re
from types import SimpleNamespace

from fastapi import FastAPI

from app.analysis import wiring
from app.config import Settings
from app.ml.classifier import Prediction
from app.ml.ner import RegexNer
from app.ml.nli import NliResult
from app.schemas import BrainResponse


class FakeLLM:
    def __init__(self, structured=None, complete=None):
        self._structured = structured or (lambda system, user, schema: None)
        self._complete = complete
        self.structured_calls: list[tuple[str, str]] = []
        self.prompts: list[str] = []

    async def structured(self, system, user, schema):
        self.structured_calls.append((schema.__name__, user))
        return self._structured(system, user, schema)

    async def complete(self, system, user):
        self.prompts.append(user)
        if self._complete is None:
            raise RuntimeError("LLM offline")
        return self._complete(system, user)

    async def answer(self, prompt, history=None, use_tools=True, tools=None):
        self.prompts.append(prompt)
        return BrainResponse(content="diff", tokens=7)


class KeywordEmbedder:
    name = "kw"

    def __init__(self, vocab):
        self.vocab = [(v,) if isinstance(v, str) else v for v in vocab]

    async def embed(self, texts):
        return [[1.0 if any(w in t.lower() for w in g) else 0.0 for g in self.vocab] for t in texts]

    async def similarity(self, a, bs):
        from app.ml.text import cosine
        vs = await self.embed([a, *bs])
        return [cosine(vs[0], v) for v in vs[1:]]


class FakeLaws:
    def __init__(self, search=None, articles=None, titles=None):
        self._search = search or []
        self.articles = articles or {}
        self._titles = titles or {}
        self.search_calls = 0

    async def search(self, query, country=None):
        self.search_calls += 1
        return self._search

    async def lookup_articles(self, code, numbers, country, max_chars=None):
        return [self.articles[code][n] for n in numbers if n in self.articles.get(code, {})]

    async def article_titles(self, code, country):
        return self._titles.get(code, [])


class FakeGraph:
    def __init__(self, laws=None):
        self.laws = laws or {}

    async def law(self, code, country):
        if code not in self.laws:
            raise RuntimeError("404")
        return self.laws[code]

    async def search_laws(self, q, country):
        return []


class FakeDocs:
    def __init__(self, texts=None):
        self.texts = texts or {}
        self.calls = []

    async def file_name(self, doc_id, user_id):
        return "Договор.docx"

    async def edited_copy(self, doc_id, user_id, edits, file_name=None):
        self.edited = (doc_id, user_id, edits, file_name)
        if getattr(self, "edited_copy_fails", False):
            raise RuntimeError("old document-service")
        return {"documentId": "copy-1", "fileName": "Договор (исправлено).docx", "applied": list(range(len(edits))),
                "skipped": []}

    async def file(self, doc_id, user_id):
        return b"PK..", "Договор (исправлено).docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    async def upload(self, user_id, file_name, content, content_type):
        self.uploaded = (user_id, file_name, content.decode("utf-8"), content_type)
        return "new-doc-1"

    async def text(self, doc_id, user_id):
        self.calls.append((doc_id, user_id))
        v = self.texts.get(doc_id)
        if isinstance(v, Exception):
            raise v
        return v


class FakeClassifier:
    def __init__(self, label="иной", score=0.0):
        self.p = Prediction(label, score, {label: score})

    async def classify(self, text, labels):
        return self.p


class FakeNli:
    def __init__(self, contradiction=0.0):
        self.c = contradiction

    async def predict(self, pairs):
        return [NliResult(0.1, 0.9 - self.c, self.c) for _ in pairs]


def make_container(llm=None, laws=None, graph=None, docs=None, embedder=None, classifier=None, nli=None):
    c = SimpleNamespace(settings=Settings(), llm=llm or FakeLLM(), laws=laws or FakeLaws(), graph=graph or FakeGraph(),
                        documents=docs or FakeDocs(), embedder=embedder or KeywordEmbedder(["x"]),
                        classifier=classifier or FakeClassifier(), nli=nli or FakeNli(), ner=RegexNer())
    return wiring.wire(c)


def make_app(c) -> FastAPI:
    app = FastAPI()
    for r in c.routers:
        app.include_router(r)
    return app


def ids_in(prompt: str) -> list[str]:
    return re.findall(r"^\[([\d.]+)\]", prompt, re.M)
