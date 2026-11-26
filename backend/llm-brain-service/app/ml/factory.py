import logging
from dataclasses import dataclass
from typing import Any, Callable

from app.config import Settings
from app.ml.classifier import CascadeClassifier, Classifier, FineTunedClassifier, LlmClassifier, PrototypeClassifier
from app.ml.embeddings import BgeM3Embedder, Embedder, TrigramEmbedder
from app.ml.ner import CompositeNer, GlinerNer, NerModel, RegexNer
from app.ml.nli import LlmNli, NliModel, TransformersNli
from app.ml.reranker import Bm25Reranker, CrossEncoderReranker, Reranker

log = logging.getLogger(__name__)

Factory = Callable[[Settings, dict[str, Any]], Any]


@dataclass(frozen=True)
class Candidate:
    name: str
    build: Factory
    needs_ml: bool = True


REGISTRY: dict[str, list[Candidate]] = {
    "embedder": [
        Candidate("bge-m3", lambda s, d: BgeM3Embedder(s.embedding_model, s.ml_device)),
        Candidate("trigram", lambda s, d: TrigramEmbedder(), needs_ml=False),
    ],
    "reranker": [
        Candidate("cross-encoder", lambda s, d: CrossEncoderReranker(s.reranker_model, s.ml_device)),
        Candidate("bm25", lambda s, d: Bm25Reranker(), needs_ml=False),
    ],
    "nli": [
        Candidate("mdeberta", lambda s, d: TransformersNli(s.nli_model, s.ml_device)),
        Candidate("llm", lambda s, d: LlmNli(d["llm"]), needs_ml=False),
    ],
    "ner": [
        Candidate("regex+gliner", lambda s, d: CompositeNer(RegexNer(), GlinerNer(s.ner_model, s.ml_device))),
        Candidate("regex", lambda s, d: RegexNer(), needs_ml=False),
    ],
    "intent": [
        Candidate("fine-tuned", lambda s, d: _fine_tuned(s.intent_model, s.ml_device)),
        Candidate("prototype", lambda s, d: PrototypeClassifier(d["embedder"]), needs_ml=False),
    ],
    "profession": [
        Candidate("fine-tuned", lambda s, d: _fine_tuned(s.profession_model, s.ml_device)),
        Candidate("prototype", lambda s, d: PrototypeClassifier(d["embedder"]), needs_ml=False),
    ],
}


def _fine_tuned(path: str, device: str) -> FineTunedClassifier:
    if not path:
        raise ValueError("model path not set")
    return FineTunedClassifier(path, device)


def build(kind: str, s: Settings, deps: dict[str, Any]) -> Any:
    for c in REGISTRY[kind]:
        if c.needs_ml and not s.ml_enabled:
            continue
        try:
            model = c.build(s, deps)
            log.info("ML %s: %s", kind, c.name)
            return model
        except Exception as e:
            log.warning("ML %s/%s unavailable: %s", kind, c.name, e)
    raise RuntimeError(f"no {kind} implementation available")


@dataclass
class MlModels:
    embedder: Embedder
    reranker: Reranker
    nli: NliModel
    ner: NerModel
    classifier: Classifier
    intent_classifier: Classifier
    profession_classifier: Classifier


def build_models(s: Settings, llm) -> MlModels:
    deps: dict[str, Any] = {"llm": llm}
    deps["embedder"] = build("embedder", s, deps)
    prototype = PrototypeClassifier(deps["embedder"])
    return MlModels(
        embedder=deps["embedder"],
        reranker=build("reranker", s, deps),
        nli=build("nli", s, deps),
        ner=build("ner", s, deps),
        classifier=CascadeClassifier(prototype, LlmClassifier(llm), s.intent_confidence),
        intent_classifier=CascadeClassifier(build("intent", s, deps), LlmClassifier(llm), s.intent_confidence),
        profession_classifier=build("profession", s, deps),
    )
