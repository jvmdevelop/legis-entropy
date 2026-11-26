from app.config import Settings
from app.container import Container
from app.graph import wiring
from app.llm.tools import ToolRegistry
from app.ml.embeddings import TrigramEmbedder
from app.ml.hybrid import HybridRanker
from app.ml.ner import RegexNer
from app.ml.nli import NliResult
from app.ml.reranker import Bm25Reranker
from app.pipeline.handlers.base import HandlerRegistry

NEUTRAL = NliResult(0.1, 0.8, 0.1)
CONTRA = NliResult(0.02, 0.08, 0.9)

DOC_TEXT = ("Трудовой договор. Работник обязан работать сверхурочно без оплаты. "
            "Работодатель вправе уволить работника без предупреждения. Заработная плата выплачивается раз в квартал.")


def law(code, title, summary=None):
    return {"code": code, "title": title, "summary": summary}


def art(code, num, title, body):
    return {"lawCode": code, "number": num, "title": title, "body": body, "source": "adilet"}


class FakeGraph:
    def __init__(self):
        self.laws = {f"L{i}": law(f"L{i}", f"Трудовой закон {i} о заработной плате и труде") for i in range(40)}
        self.laws["ТК РК"] = law("ТК РК", "Трудовой кодекс Республики Казахстан", "трудовые отношения")
        self.added: list[tuple] = []
        self.fail_add: set[str] = set()
        self.links: list[tuple] = []
        self.clause_links: list[dict] = []
        self.comments: list[dict] = []

    async def law(self, code, country):
        return {"law": {**self.laws[code], "status": "ACTIVE", "type": "CODE"}, "relationships": []} \
            if code in self.laws else None

    async def related(self, code, country):
        return [self.laws[f"L{i}"] for i in range(40)]

    async def search_laws(self, query, country):
        return list(self.laws.values())[:30]

    async def articles(self, code, country):
        return [art(code, str(n), f"Оплата труда {n}", f"Работодатель обязан оплачивать сверхурочную работу {n}. "
                                                        f"Работник вправе отказаться от сверхурочной работы.")
                for n in range(1, 8)]

    async def article(self, code, number, country):
        return art(code, number, "Сверхурочная работа", "Сверхурочная работа оплачивается в двойном размере.")

    async def search_articles(self, query, country):
        return [art("ТК РК", str(n), f"Статья {n}", "трудовой договор оплата") for n in range(15)]

    async def link_clause(self, req):
        self.clause_links.append(req)

    async def add_law(self, graph_id, code, country):
        if code in self.fail_add:
            raise RuntimeError("graphs-service 404")
        self.added.append((graph_id, code, country))

    async def link_document(self, graph_id, document_id, law_code, country, kind):
        self.links.append(("doc", document_id, law_code))

    async def link_voice(self, graph_id, voice_id, law_code, country, reason):
        self.links.append(("voice", voice_id, law_code))

    async def create_comment(self, graph_id, req):
        self.comments.append(req)
        return {"id": "c1"}


class FakeSituations:
    def __init__(self):
        self.article_conflicts: list[dict] = []
        self.doc_conflicts: list[dict] = []
        self.law_links: list[tuple] = []
        self.generated: list[tuple] = []

    async def flag_article_conflict(self, req):
        self.article_conflicts.append(req)

    async def flag_document_conflict(self, req):
        self.doc_conflicts.append(req)

    async def create(self, graph_id, user_id, title, text):
        return "sit-1"

    async def set_generated_doc(self, graph_id, situation_id, doc_id, doc_title):
        self.generated.append((situation_id, doc_id, doc_title))

    async def get(self, graph_id, situation_id):
        return {"title": "Спор", "plainText": DOC_TEXT}

    async def link_law(self, graph_id, situation_id, law_code, country, kind):
        self.law_links.append((situation_id, law_code))


class FakeDocuments:
    async def search(self, query, user_id, document_id=None):
        return [{"text": "Работник обязан работать сверхурочно без оплаты.", "documentId": document_id}]

    async def text(self, document_id, user_id):
        return DOC_TEXT


class FakeLaws:
    async def search(self, query, country=None):
        return [{"text": t} for t in ("налоговый кодекс", "трудовой договор и оплата труда", "земельный кодекс")]


class FakeTemplates:
    def __init__(self):
        self.created: list[dict] = []
        self.rendered: list[tuple] = []

    async def create(self, user_id, template):
        self.created.append(template)
        return "tpl-1"

    async def render(self, template_id, user_id, title, variables, graph_id, situation_id):
        self.rendered.append((template_id, title, variables, graph_id, situation_id))
        return "gen-1"


class FakeVoice:
    async def get(self, voice_id):
        return {"fileName": "a.ogg", "transcript": DOC_TEXT, "analysisJson": '{"articles":[{"lawCode":"ТК РК"}]}'}


class FakeLLM:
    def __init__(self, complete='["ТК РК"]', structured=None):
        self._complete, self._structured = complete, structured
        self.tools = None

    async def complete(self, system, user):
        if isinstance(self._complete, Exception):
            raise self._complete
        return self._complete

    async def structured(self, system, user, schema):
        if isinstance(self._structured, Exception):
            raise self._structured
        return self._structured


class FakeNli:
    name = "fake-nli"

    def __init__(self, result=NEUTRAL):
        self.result = result
        self.calls: list[list] = []

    async def predict(self, pairs):
        self.calls.append(pairs)
        return [self.result for _ in pairs]


def make_container(llm=None, nli=None, **settings) -> Container:
    s = Settings(**settings)
    emb, rr = TrigramEmbedder(), Bm25Reranker()
    c = Container(settings=s, http=None, documents=FakeDocuments(), graph=FakeGraph(), laws=FakeLaws(),
                  situations=FakeSituations(), templates=FakeTemplates(), voice=FakeVoice(), tools=ToolRegistry(),
                  llm=llm or FakeLLM(), embedder=emb, reranker=rr, ranker=HybridRanker(emb, rr), nli=nli or FakeNli(),
                  ner=RegexNer(), classifier=None, intent_classifier=None, profession_classifier=None,
                  handlers=HandlerRegistry())
    return Harness(c, wiring.wire(c))


class Harness:
    def __init__(self, c: Container, f):
        self.c, self.f = c, f

    def __getattr__(self, name):
        return getattr(self.c, name)
