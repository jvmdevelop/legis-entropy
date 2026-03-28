import threading
import numpy as np
from sklearn.metrics.pairwise import cosine_similarity
from sentence_transformers import SentenceTransformer

from models import (
    CompareResponse, DocumentInput, DocumentStatus,
    DocMeta, Issue, IssueKind, ReviewRequest, SearchResult, Severity,
)

# ── Small LLM for review generation ──────────────────────────────────────────

REVIEW_MODEL_NAME = "Qwen/Qwen2-0.5B-Instruct"

_llm = None
_llm_tokenizer = None
_llm_ready = False
_llm_lock = threading.Lock()


def _load_llm_background():
    global _llm, _llm_tokenizer, _llm_ready
    try:
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer
        print(f"[LLM] Loading {REVIEW_MODEL_NAME} in background…")
        tok = AutoTokenizer.from_pretrained(REVIEW_MODEL_NAME)
        mdl = AutoModelForCausalLM.from_pretrained(
            REVIEW_MODEL_NAME,
            torch_dtype=torch.float32,
        )
        mdl.eval()
        with _llm_lock:
            _llm_tokenizer = tok
            _llm = mdl
            _llm_ready = True
        print("[LLM] Model ready.")
    except Exception as e:
        print(f"[LLM] Failed to load: {e}")


def start_llm_load():
    t = threading.Thread(target=_load_llm_background, daemon=True)
    t.start()


def generate_review(prompt: str, max_new_tokens: int = 180) -> tuple[str, bool]:
    """Returns (review_text, model_ready)."""
    with _llm_lock:
        ready = _llm_ready
        tok = _llm_tokenizer
        mdl = _llm
    if not ready:
        return "Модель анализа загружается, повторите через минуту.", False

    import torch
    messages = [
        {
            "role": "system",
            "content": (
                "Ты — аналитик законодательства Республики Казахстан. "
                "Даёшь краткий профессиональный вывод на русском языке. "
                "Не повторяй исходные данные. Максимум 3–4 предложения."
            ),
        },
        {"role": "user", "content": prompt},
    ]
    text = tok.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    inputs = tok([text], return_tensors="pt")
    with torch.no_grad():
        out = mdl.generate(
            **inputs,
            max_new_tokens=max_new_tokens,
            temperature=0.7,
            do_sample=True,
            pad_token_id=tok.eos_token_id,
        )
    generated = out[0][inputs["input_ids"].shape[1]:]
    return tok.decode(generated, skip_special_tokens=True).strip(), True

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

    def review_compare(self, req: ReviewRequest) -> tuple[str, bool]:
        """Generate LLM review for a document comparison."""
        a, b = req.doc_a, req.doc_b
        pct = round(req.similarity * 100)

        assessment_map = {
            "duplicate": "практически идентичны",
            "highly_related": "тесно связаны",
            "related": "связаны",
            "independent": "независимы",
        }
        assessment_ru = assessment_map.get(req.assessment, req.assessment)

        def doc_desc(d: DocMeta) -> str:
            parts = [f"статус: {d.status}"]
            if d.ref_count: parts.append(f"{d.ref_count} ссылок на НПА")
            if d.article_count: parts.append(f"{d.article_count} статей")
            if d.is_amendment: parts.append("акт внесения изменений")
            return ", ".join(parts)

        issues_txt = ""
        if req.shared_issues:
            issues_txt = "\nОбщие проблемы: " + "; ".join(req.shared_issues[:3]) + "."

        prompt = (
            f"Проанализируй два нормативных правовых акта РК:\n"
            f"А: «{a.title}» ({doc_desc(a)})\n"
            f"Б: «{b.title}» ({doc_desc(b)})\n"
            f"Семантическое сходство: {pct}% — документы {assessment_ru}.{issues_txt}\n"
            f"Дай краткий экспертный вывод: риски, связи, что нужно проверить юристу."
        )
        return generate_review(prompt)

    def review_corpus(
        self,
        total: int,
        active: int,
        outdated: int,
        with_issues: int,
        issue_types: dict,
        most_problematic: list[str],
    ) -> tuple[str, bool]:
        """Generate LLM review for the whole corpus."""
        issue_lines = ", ".join(
            f"{k}: {v}" for k, v in issue_types.items()
        ) or "не обнаружено"
        prob_titles = "; ".join(most_problematic[:3]) or "нет данных"

        prompt = (
            f"Общий анализ корпуса НПА Республики Казахстан:\n"
            f"- Всего документов: {total}\n"
            f"- Действующих: {active}, утративших силу: {outdated}\n"
            f"- С нормативными проблемами: {with_issues}\n"
            f"- Типы проблем: {issue_lines}\n"
            f"- Наиболее проблемные НПА: {prob_titles}\n"
            f"Дай профессиональный вывод: состояние законодательной базы, "
            f"ключевые риски и приоритеты для юридического аудита."
        )
        return generate_review(prompt, max_new_tokens=220)

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
