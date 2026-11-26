from app.graph.entity_linking import LawEntityLinker
from app.ml.embeddings import TrigramEmbedder
from app.ml.reranker import Bm25Reranker
from app.pipeline.types import ContextPlan, Profession, RetrievalMode as R, TaskType as T
from app.schemas import BrainRequest
from tests.test_pipeline_fakes import FakeGraphActions, FakeLLM, build

HOUSING = {"code": "541-IV", "title": "Закон Республики Казахстан «О жилищных отношениях»"}
LAND = {"code": "442-II", "title": "Земельный кодекс Республики Казахстан"}
TAX = {"code": "120-VI", "title": "Кодекс Республики Казахстан «О налогах и других обязательных платежах»"}


class SearchGraph:
    def __init__(self, results, fail=False):
        self.results, self.fail, self.calls = results, fail, []

    async def search_laws(self, query, country):
        self.calls.append(query)
        if self.fail:
            raise RuntimeError("down")
        return self.results


class FixedEmbedder:
    name = "fixed"

    def __init__(self, scores):
        self.scores = scores

    async def similarity(self, a, bs):
        return [self.scores.get(b, 0.0) for b in bs]


class Clock:
    t = 0.0

    def __call__(self):
        return self.t


def linker(graph, embedder=None, **kw):
    return LawEntityLinker(graph, embedder or TrigramEmbedder(), Bm25Reranker(), **kw)


async def test_quoted_name_matches_graph_title_exactly():
    g = SearchGraph([LAND, HOUSING, TAX])
    r = await linker(g).link("Закон РК «О жилищных отношениях»")
    assert r and r.code == "541-IV" and r.score == 1.0


async def test_accepts_confident_candidate_with_margin():
    emb = FixedEmbedder({HOUSING["title"]: 0.9, LAND["title"]: 0.4})
    r = await linker(SearchGraph([LAND, HOUSING]), emb).link("закон о жилье")
    assert r and r.code == "541-IV"


async def test_rejects_below_threshold_and_ambiguous():
    low = FixedEmbedder({HOUSING["title"]: 0.6})
    assert await linker(SearchGraph([HOUSING]), low).link("что-то про жильё") is None
    tie = FixedEmbedder({HOUSING["title"]: 0.86, LAND["title"]: 0.84})
    assert await linker(SearchGraph([LAND, HOUSING]), tie).link("закон о земле и жилье") is None


async def test_cache_positive_and_negative_with_ttl():
    clock = Clock()
    g = SearchGraph([HOUSING])
    lk = linker(g, FixedEmbedder({HOUSING["title"]: 0.95}), clock=clock, negative_ttl_s=10)
    await lk.link("Жилищный закон")
    await lk.link("жилищный закон ")
    assert len(g.calls) == 1
    g.results = []
    await lk.link("неизвестный закон")
    await lk.link("неизвестный закон")
    assert len(g.calls) == 2
    clock.t = 11
    await lk.link("неизвестный закон")
    assert len(g.calls) == 3


async def test_search_failure_and_no_candidates():
    assert await linker(SearchGraph([], fail=True)).link("Закон РК «О жилищных отношениях»") is None
    assert await linker(SearchGraph([{"title": "без кода"}])).link("Закон РК «О чём-то»") is None


async def test_handler_adds_linked_law_from_answer_name():
    actions = FakeGraphActions()
    answer = "Порядок выселения регулирует Закон РК «О жилищных отношениях», применяйте его нормы в споре."
    _, h = build(llm=FakeLLM(answer), actions=actions)
    h.linker = linker(SearchGraph([LAND, HOUSING]))
    plan = ContextPlan(T.LAW_SEARCH, R.LAW, professions=(Profession.GENERAL_LAWYER,))
    r = await h.handle(BrainRequest(message="выселение", graph_id="g"), plan)
    assert any("541-IV" in call[1] for call in actions.calls)
    assert r.content.rstrip().endswith("541-IV")
