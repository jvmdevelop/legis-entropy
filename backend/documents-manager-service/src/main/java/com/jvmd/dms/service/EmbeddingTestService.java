package com.jvmd.dms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingTestService {

    private final EmbeddingModel embeddingModel;

    public void testRussianEmbeddings() {
        List<String> russianTexts = List.of(
            "Закон о защите прав потребителей",
            "Гражданский кодекс Российской Федерации",
            "Налоговый кодекс",
            "Трудовой кодекс",
            "Конституция страны"
        );

        log.info("Тестирование русской модели эмбеддингов...");

        for (String text : russianTexts) {
            try {
                EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
                float[] embedding = response.getResults().get(0).getOutput();

                log.info("Текст: '{}'", text);
                log.info("Размерность эмбеддинга: {}", embedding.length);
                log.info("Первые 5 значений: [{}, {}, {}, {}, {}]",
                    embedding[0], embedding[1], embedding[2], embedding[3], embedding[4]);
                log.info("---");

            } catch (Exception e) {
                log.error("Ошибка при обработке текста '{}': {}", text, e.getMessage());
            }
        }

        testSemanticSimilarity();
    }

    private void testSemanticSimilarity() {
        try {
            String text1 = "Закон о защите прав потребителей";
            String text2 = "Права граждан при покупке товаров";
            String text3 = "Уголовный кодекс";

            float[] embedding1 = embeddingModel.embed(text1);
            float[] embedding2 = embeddingModel.embed(text2);
            float[] embedding3 = embeddingModel.embed(text3);

            double similarity12 = cosineSimilarity(embedding1, embedding2);
            double similarity13 = cosineSimilarity(embedding1, embedding3);

            log.info("Семантическая схожесть:");
            log.info("'{}' и '{}': {:.4f}", text1, text2, similarity12);
            log.info("'{}' и '{}': {:.4f}", text1, text3, similarity13);

            if (similarity12 > similarity13) {
                log.info("✅ Модель правильно определяет семантическую близость русских текстов");
            } else {
                log.warn("⚠️ Возможные проблемы с семантическим анализом русских текстов");
            }

        } catch (Exception e) {
            log.error("Ошибка при тестировании семантической схожести: {}", e.getMessage());
        }
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Векторы должны иметь одинаковую размерность");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
