package com.jvmd.lawservice.storage;

import com.jvmd.lawservice.model.Law;
import com.jvmd.lawservice.model.LawCategory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategorizedLawStorage {

    private final VectorStore vectorStore;
    private final ArticleAwareLawSplitter articleAwareLawSplitter;
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
        "(?m)^(?:Статья|СТАТЬЯ|статья)\\s+(\\d+)(?:[^\\n]{0,120})?",
        Pattern.MULTILINE | Pattern.CASE_INSENSITIVE
    );

    public void save(Law law) {
        try {
            if (law.getCategory() == null) {
                law.setCategory(LawCategory.fromTitle(law.getTitle()));
            }

            if (law.getCountry() == null) {
                law.setCountry(determineCountryFromUrl(law.getSourceUrl()));
            }

            Document doc = new Document(law.getContent(), law.getMetaData());
            doc.getMetadata().put("collection", law.getVectorStoreCollection());

            List<Document> chunks = articleAwareLawSplitter.apply(List.of(doc));

            int lastArticleNumber = 0;
            String lastArticleTitle = "";
            String lawCode = categoryToShortCode(law.getCategory());
            String lawTitle = law.getTitle() != null ? law.getTitle() : "";
            String lawDocumentNumber =
                law.getDocumentNumber() != null ? law.getDocumentNumber() : "";

            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                chunk
                    .getMetadata()
                    .put("collection", law.getVectorStoreCollection());
                chunk.getMetadata().put("chunkIndex", i);
                chunk.getMetadata().put("chunkCount", chunks.size());

                String chunkText =
                    chunk.getText() != null ? chunk.getText() : "";
                Matcher matcher = ARTICLE_PATTERN.matcher(chunkText);
                if (matcher.find()) {
                    lastArticleNumber = Integer.parseInt(matcher.group(1));
                    lastArticleTitle = matcher
                        .group(0)
                        .replaceAll("(?:Статья|СТАТЬЯ|статья)\\s+\\d+\\s*", "")
                        .trim();
                    if (lastArticleTitle.length() > 120) {
                        lastArticleTitle = lastArticleTitle.substring(0, 120);
                    }
                }

                if (lastArticleNumber > 0) {
                    chunk.getMetadata().put("articleNumber", lastArticleNumber);
                    chunk.getMetadata().put("articleTitle", lastArticleTitle);
                }

                if (!lawCode.isEmpty()) {
                    chunk.getMetadata().put("lawCode", lawCode);
                }
                chunk.getMetadata().put("lawTitle", lawTitle);
                chunk.getMetadata().put("lawDocumentNumber", lawDocumentNumber);
            }

            vectorStore.add(chunks);

            log.info(
                "Сохранен закон '{}' в коллекцию: {} (категория: {}, страна: {}, чанков: {})",
                law.getTitle(),
                law.getVectorStoreCollection(),
                law.getCategory(),
                law.getCountry(),
                chunks.size()
            );
        } catch (Exception e) {
            log.error(
                "Ошибка при сохранении закона '{}': {}",
                law.getTitle(),
                e.getMessage(),
                e
            );
            throw new RuntimeException("Не удалось сохранить закон", e);
        }
    }

    public List<Document> search(
        String query,
        String country,
        LawCategory category
    ) {
        try {
            String collectionName = buildCollectionName(country, category);

            SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(10)
                .similarityThreshold(0.4)
                .filterExpression("collection == '" + collectionName + "'")
                .build();

            List<Document> results = vectorStore.similaritySearch(
                searchRequest
            );

            log.info(
                "Найдено {} документов в коллекции {} по запросу '{}'",
                results.size(),
                collectionName,
                query
            );

            return results;
        } catch (Exception e) {
            log.error(
                "Ошибка при поиске в коллекции {}: {}",
                buildCollectionName(country, category),
                e.getMessage(),
                e
            );
            return List.of();
        }
    }

    public List<Document> searchAll(String query) {
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(10)
                .similarityThreshold(0.4)
                .build();

            List<Document> results = vectorStore.similaritySearch(
                searchRequest
            );
            log.info(
                "Найдено {} документов по запросу '{}' (во всех коллекциях)",
                results.size(),
                query
            );
            return results;
        } catch (Exception e) {
            log.error(
                "Ошибка при поиске по всем коллекциям: {}",
                e.getMessage(),
                e
            );
            return List.of();
        }
    }

    public List<Document> searchByCountry(String query, String country) {
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(15)
                .similarityThreshold(0.4)
                .filterExpression("country == '" + country + "'")
                .build();

            List<Document> results = vectorStore.similaritySearch(
                searchRequest
            );
            log.info(
                "Найдено {} документов для страны {} по запросу '{}'",
                results.size(),
                country,
                query
            );
            return results;
        } catch (Exception e) {
            log.error(
                "Ошибка при поиске по стране {}: {}",
                country,
                e.getMessage(),
                e
            );
            return List.of();
        }
    }

    public List<Document> searchByCategory(String query, LawCategory category) {
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(15)
                .similarityThreshold(0.4)
                .filterExpression("category == '" + category.name() + "'")
                .build();

            List<Document> results = vectorStore.similaritySearch(
                searchRequest
            );
            log.info(
                "Найдено {} документов категории {} по запросу '{}'",
                results.size(),
                category,
                query
            );
            return results;
        } catch (Exception e) {
            log.error(
                "Ошибка при поиске по категории {}: {}",
                category,
                e.getMessage(),
                e
            );
            return List.of();
        }
    }

    public Map<String, Integer> getCollectionStatistics() {
        Map<String, Integer> stats = new ConcurrentHashMap<>();

        try {
            for (String country : List.of("KZ", "RU", "UNKNOWN")) {
                for (LawCategory category : LawCategory.values()) {
                    String collectionName = buildCollectionName(
                        country,
                        category
                    );

                    SearchRequest searchRequest = SearchRequest.builder()
                        .query("")
                        .topK(1)
                        .filterExpression(
                            "metadata['collection'] == '" + collectionName + "'"
                        )
                        .build();

                    try {
                        List<Document> results = vectorStore.similaritySearch(
                            searchRequest
                        );
                        if (!results.isEmpty()) {
                            stats.put(collectionName, results.size());
                        }
                    } catch (Exception e) {}
                }
            }
        } catch (Exception e) {
            log.warn("Ошибка при получении статистики: {}", e.getMessage());
        }

        return stats;
    }

    private String buildCollectionName(String country, LawCategory category) {
        return (
            (country != null ? country.toLowerCase() : "unknown") +
            "_" +
            (category != null ? category.name().toLowerCase() : "unknown")
        );
    }

    private String determineCountryFromUrl(String url) {
        if (url == null) return "UNKNOWN";

        if (url.contains("zan.kz") || url.contains("adilet.zan.kz")) {
            return "KZ";
        } else if (
            url.contains("pravo.gov.ru") || url.contains("consultant.ru")
        ) {
            return "RU";
        }

        return "UNKNOWN";
    }

    private String categoryToShortCode(LawCategory category) {
        if (category == null) return "";

        return switch (category) {
            case CIVIL -> "ГК";
            case LABOR -> "ТК";
            case CRIMINAL -> "УК";
            case FAMILY -> "СК";
            case TAX -> "НК";
            case HOUSING -> "ЖК";
            case LAND -> "ЗК";
            default -> "";
        };
    }
}
