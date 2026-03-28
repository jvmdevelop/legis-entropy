import numpy as np
from config import (
    COMPARE_DUPLICATE_THRESHOLD,
    COMPARE_HIGHLY_RELATED_THRESHOLD,
    COMPARE_RELATED_THRESHOLD,
    CONTRADICTION_HIGH,
    CONTRADICTION_LOW,
    DUPLICATE_THRESHOLD,
    EMBED_TEXT_LIMIT,
    OPPOSING_PAIRS,
    SEARCH_TEXT_LIMIT,
)
from llm import LLMService
from models import (
    CompareResponse,
    DocMeta,
    DocumentInput,
    DocumentStatus,
    Issue,
    IssueKind,
    ReviewRequest,
    SearchResult,
    Severity,
)
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity


class DocumentAnalyzer:
    def __init__(
        self, model: SentenceTransformer, device: str, llm: LLMService
    ) -> None:
        self._model = model
        self._device = device
        self._llm = llm

    def analyze(self, docs: list[DocumentInput]) -> list[Issue]:
        active = [
            d for d in docs if d.status == DocumentStatus.active and d.text.strip()
        ]
        if len(active) < 2:
            return []

        sim_matrix = cosine_similarity(self._embed(active))
        issues: list[Issue] = []

        for i in range(len(active)):
            for j in range(i + 1, len(active)):
                score = float(sim_matrix[i, j])
                doc_a, doc_b = active[i], active[j]

                if score >= DUPLICATE_THRESHOLD:
                    issues.append(_duplication_issue(doc_a, doc_b, score))
                elif CONTRADICTION_LOW <= score < CONTRADICTION_HIGH:
                    if _has_opposing_keywords(doc_a.text, doc_b.text):
                        issues.append(_contradiction_issue(doc_a, doc_b, score))

        return issues

    def search(
        self, query: str, docs: list[DocumentInput], top_k: int
    ) -> list[SearchResult]:
        if not docs:
            return []
        texts = [f"{d.title}. {d.text[:SEARCH_TEXT_LIMIT]}" for d in docs]
        query_emb = self._model.encode(
            [query], device=self._device, normalize_embeddings=True
        )
        doc_embs = self._model.encode(
            texts,
            device=self._device,
            normalize_embeddings=True,
            show_progress_bar=False,
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
            f"{doc_a.title}. {doc_a.text[:SEARCH_TEXT_LIMIT]}",
            f"{doc_b.title}. {doc_b.text[:SEARCH_TEXT_LIMIT]}",
        ]
        embs = self._model.encode(texts, device=self._device, normalize_embeddings=True)
        similarity = float(embs[0] @ embs[1])
        return CompareResponse(
            similarity=similarity, assessment=_classify_similarity(similarity)
        )

    def review_compare(self, req: ReviewRequest) -> tuple[str, bool]:
        return self._llm.generate(_build_compare_prompt(req))

    def review_corpus(
        self,
        total: int,
        active: int,
        outdated: int,
        with_issues: int,
        issue_types: dict,
        most_problematic: list[str],
    ) -> tuple[str, bool]:
        return self._llm.generate(
            _build_corpus_prompt(
                total, active, outdated, with_issues, issue_types, most_problematic
            ),
            max_new_tokens=220,
        )

    def _embed(self, docs: list[DocumentInput]) -> np.ndarray:
        texts = [f"{d.title}. {d.text[:EMBED_TEXT_LIMIT]}" for d in docs]
        return self._model.encode(
            texts,
            device=self._device,
            batch_size=32,
            normalize_embeddings=True,
            show_progress_bar=False,
        )


def _classify_similarity(similarity: float) -> str:
    if similarity >= COMPARE_DUPLICATE_THRESHOLD:
        return "duplicate"
    if similarity >= COMPARE_HIGHLY_RELATED_THRESHOLD:
        return "highly_related"
    if similarity >= COMPARE_RELATED_THRESHOLD:
        return "related"
    return "independent"


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


def _build_compare_prompt(req: ReviewRequest) -> str:
    a, b = req.doc_a, req.doc_b
    pct = round(req.similarity * 100)
    assessment_map = {
        "duplicate": "практически идентичны",
        "highly_related": "тесно связаны",
        "related": "связаны",
        "independent": "независимы",
    }
    assessment_ru = assessment_map.get(req.assessment, req.assessment)

    def _doc_desc(d: DocMeta) -> str:
        parts = [f"статус: {d.status}"]
        if d.ref_count:
            parts.append(f"{d.ref_count} ссылок на НПА")
        if d.article_count:
            parts.append(f"{d.article_count} статей")
        if d.is_amendment:
            parts.append("акт внесения изменений")
        return ", ".join(parts)

    issues_txt = ""
    if req.shared_issues:
        issues_txt = "\nОбщие проблемы: " + "; ".join(req.shared_issues[:3]) + "."

    return (
        f"Проанализируй два нормативных правовых акта РК:\n"
        f"А: «{a.title}» ({_doc_desc(a)})\n"
        f"Б: «{b.title}» ({_doc_desc(b)})\n"
        f"Семантическое сходство: {pct}% — документы {assessment_ru}.{issues_txt}\n"
        f"Дай краткий экспертный вывод: риски, связи, что нужно проверить юристу."
    )


def _build_corpus_prompt(
    total: int,
    active: int,
    outdated: int,
    with_issues: int,
    issue_types: dict,
    most_problematic: list[str],
) -> str:
    issue_lines = (
        ", ".join(f"{k}: {v}" for k, v in issue_types.items()) or "не обнаружено"
    )
    prob_titles = "; ".join(most_problematic[:3]) or "нет данных"
    return (
        f"Общий анализ корпуса НПА Республики Казахстан:\n"
        f"- Всего документов: {total}\n"
        f"- Действующих: {active}, утративших силу: {outdated}\n"
        f"- С нормативными проблемами: {with_issues}\n"
        f"- Типы проблем: {issue_lines}\n"
        f"- Наиболее проблемные НПА: {prob_titles}\n"
        f"Дай профессиональный вывод: состояние законодательной базы, "
        f"ключевые риски и приоритеты для юридического аудита."
    )


def _duplication_issue(a: DocumentInput, b: DocumentInput, score: float) -> Issue:
    pct = round(score * 100)
    severity = (
        Severity.high
        if score >= 0.93
        else (Severity.medium if score >= 0.87 else Severity.low)
    )
    return Issue(
        kind=IssueKind.duplication,
        severity=severity,
        document_ids=[a.id, b.id],
        explanation=f"«{a.title}» и «{b.title}» семантически совпадают на {pct}%",
        score=score,
    )


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
