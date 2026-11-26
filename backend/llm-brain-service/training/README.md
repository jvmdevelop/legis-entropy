# training
fine-tune rubert-tiny2 (29M, ~1 GB VRAM, fp16) | задачи: intent / profession / risk

## venv
- app deps: .venv (generate / verify)
- torch CUDA: training/.venv (train)

## шаги (из backend/llm-brain-service)
- env: set -a; . ../../.env; set +a  | нужен LLM_API_KEY или NVIDIA_API_KEY
- данные: .venv/bin/python -m training.generate <task> [--per-class N]  | дозаписывает training/data/<task>.jsonl
- фильтр шума: .venv/bin/python -m training.verify intent|risk  | LLM-судья, в обучение идут только verified
- обучение: training/.venv/bin/python training/train.py <task> [--epochs --lr --batch]
- результат: models/<task>/ + metrics.json (val + ручной eval training/eval/)

## гиперпараметры (RTX 2050 4GB, < 2 мин на задачу)
- intent: --epochs 15 --lr 1.5e-4 --batch 16
- profession: --epochs 25 --lr 2e-4 --batch 16
- risk: --epochs 12 --lr 1.5e-4 --batch 16

## метрики macro-F1 (val / ручной eval)
- intent: 0.978 / 0.92
- profession: 0.897 / 0.808
- risk: 0.896 / 0.694 | числовые нормы ТК -> rule-based проверка; низкая уверенность -> LLM

## подключение
- compose: models/ -> /fine-tuned:ro | INTENT_MODEL / PROFESSION_MODEL / RISK_MODEL
- активно только при ML_ENABLED=true (образ с ML_EXTRAS=ml) | иначе fallback prototype/LLM

## NER (GLiNER, без дообучения)
- eval: training/.venv/bin/python training/eval_ner.py [--thresholds 0.3 0.4 0.5] | набор training/eval/ner.jsonl (20 предл. / 44 сущн.)
- P/R: regex 1.00/0.66 | gliner@0.4 0.97/0.77 | regex+gliner@0.4 0.98/0.98 (в сервисе)
- постфильтр: роли сторон не org; sum/term только с числом
- fine-tune не нужен; если на реальных договорах полнота упадёт -> разметка + дообучение
