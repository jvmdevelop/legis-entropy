import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from sentence_transformers import SentenceTransformer

from models import (
    CompareResponse, DocumentInput, DocumentStatus,
    Issue, IssueKind, SearchResult, Severity,
)

# Threshold above which two active documents are considered duplicates.
DUPLICATE_THRESHOLD = 0.82

# Range [low, high] in which we look for contradictions:
# similar enough to be about the same topic, different enough to potentially conflict.
CONTRADICTION_LOW = 0.55
CONTRADICTION_HIGH = 0.82

# Legal keyword pairs whose co-occurrence in semantically close documents
# suggests a normative conflict.
OPPOSING_PAIRS: list[tuple[list[str], list[str]]] = [
    (
        ["запрещается", "не допускается", "не вправе", "не может", "запрет", "нельзя"],
        ["разрешается", "допускается", "вправе", "может", "имеет право", "разрешено"],
    ),
    (
        ["обязан", "должен", "обязательно", "необходимо", "требуется"],
        ["не обязан", "не должен", "добровольно", "по желанию", "вправе отказаться"],
    ),
    (
        ["увеличивается", "повышается", "возрастает"],
        ["уменьшается", "снижается", "сокращается"],
    ),
]


def _has_opposing_keywords(text_a: str, text_b: str) -> bool:
    a, b = text_a.lower(), text_b.lower()
    for positives, negatives in OPPOSING_PAIRS:
        a_pos = any(kw in a for kw in positives)
        a_neg = any(kw in a for kw in negatives)
        b_pos = any(kw in b for kw in positives)
        b_neg = any(kw in b for kw in negatives)
        if (a_pos and b_neg) or (a_neg and b_pos):
            return True
    return False


class DocumentAnalyzer:
    def __init__(self, model: SentenceTransformer, device: str) -> None:
        self._model = model
        self._device = device

    def analyze(self, docs: list[DocumentInput]) -> list[Issue]:
        # Only analyze active documents that have text content.
        active = [d for d in docs if d.status == DocumentStatus.active and d.text.strip()]
        if len(active) < 2:
            return []

        embeddings = self._embed(active)
        sim_matrix = cosine_similarity(embeddings)

        issues: list[Issue] = []
        seen: set[tuple[int, int]] = set()

        for i in range(len(active)):
            for j in range(i + 1, len(active)):
                key = (i, j)
                if key in seen:
                    continue
                seen.add(key)

                score: float = float(sim_matrix[i, j])
                doc_a, doc_b = active[i], active[j]

                if score >= DUPLICATE_THRESHOLD:
                    issues.append(self._duplication_issue(doc_a, doc_b, score))

                elif CONTRADICTION_LOW <= score < CONTRADICTION_HIGH:
                    if _has_opposing_keywords(doc_a.text, doc_b.text):
                        issues.append(self._contradiction_issue(doc_a, doc_b, score))

        return issues

    def search(self, query: str, docs: list[DocumentInput], top_k: int) -> list[SearchResult]:
        if not docs:
            return []
        texts = [f"{d.title}. {d.text[:500]}" for d in docs]
        query_emb = self._model.encode(
            [query], device=self._device, normalize_embeddings=True
        )
        doc_embs = self._model.encode(
            texts, device=self._device, normalize_embeddings=True, show_progress_bar=False
        )
        scores = (query_emb @ doc_embs.T).flatten()
        top_indices = scores.argsort()[::-1][:top_k]
        return [
            SearchResult(id=docs[i].id, title=docs[i].title, score=float(scores[i]))
            for i in top_indices
            if scores[i] > 0.1
        ]

    def compare(self, doc_a: DocumentInput, doc_b: DocumentInput) -> CompareResponse:
        texts = [
            f"{doc_a.title}. {doc_a.text[:500]}",
            f"{doc_b.title}. {doc_b.text[:500]}",
        ]
        embs = self._model.encode(texts, device=self._device, normalize_embeddings=True)
        similarity = float(embs[0] @ embs[1])
        if similarity >= 0.93:
            assessment = "duplicate"
        elif similarity >= 0.82:
            assessment = "highly_related"
        elif similarity >= 0.55:
            assessment = "related"
        else:
            assessment = "independent"
        return CompareResponse(similarity=similarity, assessment=assessment)

    def _embed(self, docs: list[DocumentInput]) -> np.ndarray:
        # Concatenate title + first part of text for a richer signal.
        texts = [f"{d.title}. {d.text[:2000]}" for d in docs]
        return self._model.encode(
            texts,
            device=self._device,
            batch_size=32,
            normalize_embeddings=True,
            show_progress_bar=False,
        )

    @staticmethod
    def _duplication_issue(a: DocumentInput, b: DocumentInput, score: float) -> Issue:
        pct = round(score * 100)
        severity = Severity.high if score >= 0.93 else (Severity.medium if score >= 0.87 else Severity.low)
        return Issue(
            kind=IssueKind.duplication,
            severity=severity,
            document_ids=[a.id, b.id],
            explanation=f"«{a.title}» и «{b.title}» семантически совпадают на {pct}%",
            score=score,
        )

    @staticmethod
    def _contradiction_issue(a: DocumentInput, b: DocumentInput, score: float) -> Issue:
        pct = round(score * 100)
        return Issue(
            kind=IssueKind.contradiction,
            severity=Severity.high,
            document_ids=[a.id, b.id],
            explanation=(
                f"«{a.title}» и «{b.title}» схожи на {pct}%, "
                "но содержат противоположные нормативные предписания"
            ),
            score=score,
        )
