# ── Model names ───────────────────────────────────────────────────────────────

BERT_MODEL_NAME = "cointegrated/rubert-tiny2"
LLM_MODEL_NAME = "Qwen/Qwen2-0.5B-Instruct"

# ── Similarity thresholds ─────────────────────────────────────────────────────

# Above this → documents are considered semantic duplicates.
DUPLICATE_THRESHOLD = 0.82

# Within [low, high) → similar enough to share a topic, different enough to conflict.
CONTRADICTION_LOW = 0.55
CONTRADICTION_HIGH = 0.82

# Similarity breakpoints for compare() assessment labels.
COMPARE_DUPLICATE_THRESHOLD = 0.93
COMPARE_HIGHLY_RELATED_THRESHOLD = 0.82
COMPARE_RELATED_THRESHOLD = 0.55

# ── Text truncation ───────────────────────────────────────────────────────────

EMBED_TEXT_LIMIT = 2000    # chars used when building corpus embeddings
SEARCH_TEXT_LIMIT = 500    # chars used for search & compare

# ── Legal keyword pairs ───────────────────────────────────────────────────────
# Co-occurrence of opposing terms in semantically-similar documents signals a conflict.

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
