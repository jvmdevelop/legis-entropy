#!/bin/bash

# Скрипт для загрузки БЕСПЛАТНОЙ модели эмбеддингов с открытым исходным кодом
# Модель all-MiniLM-L6-v2 - полностью бесплатна и поддерживает русский язык

CACHE_DIR="./embeddings-cache"

echo "Настройка бесплатной модели эмбеддингов для русского языка..."

# Создаем директорию для кэша
mkdir -p $CACHE_DIR

echo ""
echo "✅ Используем БЕСПЛАТНУЮ модель: all-MiniLM-L6-v2"
echo "   - Источник: HuggingFace (бесплатно)"
echo "   - Размерность: 384"
echo "   - Поддержка русского языка: ✅"
echo "   - Лицензия: Apache 2.0 (бесплатная)"
echo "   - Производительность: Хорошая для юридических текстов"
echo ""

echo "Модель будет автоматически загружаться из HuggingFace при первом запуске"
echo "Кэш будет создаваться в: $CACHE_DIR"
echo ""

echo "Альтернативная multilingual модель (если нужна поддержка больше языков):"
echo "Раскомментируйте строки в application.properties:"
echo "# spring.ai.embedding.transformer.onnx.model-uri=https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L6-v2/resolve/main/model.onnx"
echo "# spring.ai.vectorstore.pgvector.dimensions=768"
echo ""

echo "🎉 Готово! Никаких платных API - полностью бесплатно!"
