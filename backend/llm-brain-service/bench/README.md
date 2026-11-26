# bench
юр. бенчмарк аудита: документ + известные правильные ответы -> метрики

## запуск (из backend/llm-brain-service)
- offline: .venv/bin/python -m bench.run --offline  | только правила/реестры, без law-service и LLM
- live: set -a; . ../../.env; set +a; .venv/bin/python -m bench.run --live  | нужен поднятый docker-стек
- опции: --case <имя> | --json out.json

## метрики
- reference_errors: ошибочные ссылки найдены / ложные ошибки на валидных
- correct_article: названа ли правильная статья
- repealed: утратившие силу акты
- violations: нарушения по существу (сроки, отпуск, ст. 37)
- missing_clauses: обязательные пункты (ст. 28 ТК РК)
- requisites: ошибки в номерах кодексов

## кейсы (bench/cases/*.json)
- поля: document | contract_type | references[clause, law, cited, verdict OK/WRONG_ARTICLE, correct] | repealed | valid_acts | violations | missing_clauses | requisites
- work_contract_kz: справка теста + поправки юриста | на вход идёт _clean версия (без ответов)
- ВАЖНО: таблица app/analysis/topics.py собрана по этому же кейсу -> offline-цифры завышены
- нужны новые кейсы от юристов: реальные договоры + разбор

## результаты work_contract_kz (2026-11-26)
- offline: ссылки P 1.00 / R 0.89 (нет п. 10.3: без базы не проверить) | остальное 1.00
- live (law-service + NVIDIA LLM, 5 прогонов): ссылки, правильная статья, утратившие силу, нарушения, обяз. пункты — 1.00 во всех
- imprecise (п. 1.3 ст. 33): найдено 3/5, ложных 0 | цена точности: перепроверка LLM повторным вызовом
- live требует law-service с параметром maxChars у /api/laws/articles/lookup (полный текст статьи)
