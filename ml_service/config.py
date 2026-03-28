BERT_MODEL_NAME = "cointegrated/rubert-tiny2"
LLM_MODEL_NAME = "Qwen/Qwen2-0.5B-Instruct"


DUPLICATE_THRESHOLD = 0.82

CONTRADICTION_LOW = 0.55
CONTRADICTION_HIGH = 0.82

COMPARE_DUPLICATE_THRESHOLD = 0.93
COMPARE_HIGHLY_RELATED_THRESHOLD = 0.82
COMPARE_RELATED_THRESHOLD = 0.55


EMBED_TEXT_LIMIT = 2000
SEARCH_TEXT_LIMIT = 500


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
