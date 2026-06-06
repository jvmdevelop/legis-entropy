package com.jvmd.llmbrainservice.config;

import com.jvmd.llmbrainservice.client.DmsClient;
import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.client.LawClient;
import com.jvmd.llmbrainservice.client.SituationClient;
import com.jvmd.llmbrainservice.dto.*;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import com.jvmd.llmbrainservice.service.graph.GraphActionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
@Slf4j
public class AITools {

    @Bean
    @Description("Поиск в базе нормативно-правовых актов (законов) Казахстана и РФ")
    public Function<LawSearchRequest, List<RetrievalChunkResponse>> searchLaws(LawClient lawClient) {
        return request -> {
            log.info("Tool searchLaws called with query: {}", request.query());
            return lawClient.searchLaws(request.query());
        };
    }

    @Bean
    @Description("Поиск в загруженных пользователем документах (договорах, соглашениях)")
    public Function<UserDocSearchRequest, List<RetrievalChunkResponse>> searchUserDocuments(DmsClient dmsClient) {
        return request -> {
            log.info("Tool searchUserDocuments called for user {} with query: {}", request.userId(), request.query());
            return dmsClient.searchUserDocuments(
                    request.query(),
                    request.userId(),
                    request.documentId()
            );
        };
    }

    @Bean
    @Description("Получить полную информацию о законе, включая его связи с другими нормативными актами. " +
            "code - краткое или полное название/код закона (например, 'ГК', 'Гражданский кодекс', '141-VII ЗРК'), " +
            "country - страна (RK или RF)")
    public Function<LawGraphSearchRequest, String> getLawGraph(GraphServiceClient graphServiceClient) {
        return request -> {
            log.info("Tool getLawGraph called for {} in {}", request.code(), request.country());

            try {
                LawGraphResponse lawGraph = graphServiceClient.getLawWithRelationships(request.code(), request.country());
                if (lawGraph != null) {
                    return lawGraph.formatForLLM();
                }
            } catch (Exception e) {
                log.debug("Direct lookup failed for {}, attempting search fallback. Error: {}", request.code(), e.getMessage());
            }

            try {
                List<LawInfo> searchResults = graphServiceClient.searchLaws(request.code(), request.country());
                if (searchResults != null && !searchResults.isEmpty()) {
                    LawInfo firstResult = searchResults.get(0);
                    log.info("Fallback search found law: {}", firstResult.code());

                    LawGraphResponse retryLawGraph = graphServiceClient.getLawWithRelationships(firstResult.code(), request.country());
                    if (retryLawGraph != null) {
                        return retryLawGraph.formatForLLM();
                    }
                }
            } catch (Exception e) {
                log.warn("Fallback search or retry failed for {}: {}", request.code(), e.getMessage());
            }

            return "Закон '" + request.code() + "' не найден в графе норм. Попробуйте уточнить название или код.";
        };
    }

    @Bean
    @Description("Найти все законы, связанные с заданным законом (ссылаются на него, изменяют его, определяют его). " +
            "Полезно для понимания правового контекста и зависимостей.")
    public Function<LawRelatedSearchRequest, String> findRelatedLaws(GraphServiceClient graphServiceClient) {
        return request -> {
            log.info("Tool findRelatedLaws called for {} in {}", request.code(), request.country());
            try {
                List<LawInfo> laws = graphServiceClient.findRelatedLaws(request.code(), request.country());
                if (laws == null || laws.isEmpty()) {
                    return "Не найдены законы, связанные с " + request.code() + ".";
                }
                return laws.stream()
                        .map(law -> "- **" + law.code() + "**: " + law.title() +
                                (law.summary() != null ? " (" + law.summary() + ")" : ""))
                        .collect(Collectors.joining("\n"));
            } catch (Exception e) {
                log.warn("Failed to find related laws for {}: {}", request.code(), e.getMessage());
                return "Ошибка при поиске связанных законов для " + request.code() + ".";
            }
        };
    }

    @Bean
    @Description("Добавить закон в текущий граф пользователя (GraphView). " +
            "Параметр graphId должен совпадать с активным ID графа из системного контекста. " +
            "code — код или название закона, country — RK или RF (по умолчанию RK).")
    public Function<AddLawToGraphRequest, String> addLawToUserGraph(GraphServiceClient graphServiceClient) {
        return request -> {
            log.info("Tool addLawToUserGraph called: graphId={}, code={}", request.graphId(), request.code());
            String country = request.country() == null ? "RK" : request.country();

            try {
                graphServiceClient.addLawToUserGraph(request.graphId(), request.code(), country);
                return "Закон " + request.code() + " добавлен в граф.";
            } catch (Exception e) {
                log.debug("Direct add failed for {}, attempting search fallback", request.code());
                try {
                    List<LawInfo> found = graphServiceClient.searchLaws(request.code(), country);
                    if (found != null && !found.isEmpty()) {
                        LawInfo first = found.get(0);
                        graphServiceClient.addLawToUserGraph(request.graphId(), first.code(), country);
                        return "Добавлен закон " + first.code() + ": " + first.title();
                    }
                } catch (Exception ex) {
                    log.warn("Fallback search and add failed for {}: {}", request.code(), ex.getMessage());
                }
                return "Не удалось добавить закон '" + request.code() + "' — не найден в базе.";
            }
        };
    }

    @Bean
    @Description("Найти законы по смысловому запросу и автоматически добавить топ-K результатов в текущий граф пользователя. " +
            "Полезно когда юрист просит «найди все законы про защиту прав потребителей и добавь в граф». " +
            "graphId — ID активного графа из системного контекста, query — что искать, limit — сколько добавить (по умолчанию 5).")
    public Function<FindAndAddRelatedLawsRequest, String> findAndAddRelatedLawsToGraph(GraphServiceClient graphServiceClient) {
        return request -> {
            log.info("Tool findAndAddRelatedLawsToGraph called: graphId={}, query={}", request.graphId(), request.query());
            String country = request.country() == null ? "RK" : request.country();
            int limit = request.limit() == null ? 5 : Math.min(request.limit(), 10);

            try {
                List<LawInfo> laws = graphServiceClient.searchLaws(request.query(), country);
                if (laws == null || laws.isEmpty()) {
                    return "Ничего не найдено по запросу «" + request.query() + "».";
                }

                int added = 0;
                StringBuilder report = new StringBuilder("Найдено и добавлено в граф:\n");
                for (LawInfo law : laws.stream().limit(limit).toList()) {
                    try {
                        graphServiceClient.addLawToUserGraph(request.graphId(), law.code(), country);
                        added++;
                        report.append("- **").append(law.code()).append("**: ").append(law.title()).append("\n");
                    } catch (Exception e) {
                        log.warn("Failed to add specific law {} from search results to graph: {}", law.code(), e.getMessage());
                    }
                }
                return added == 0 ? "Ни один закон не удалось добавить в граф." : report.toString();
            } catch (Exception e) {
                log.warn("Failed to execute findAndAddRelatedLawsToGraph: {}", e.getMessage());
                return "Произошла ошибка при поиске и добавлении законов.";
            }
        };
    }

    @Bean
    @Description("Получить конкретную статью закона по её номеру. " +
            "code — код закона (например, 'ГК'), number — номер статьи (например, '350'), country — RK или RF. " +
            "Возвращает заголовок, тело статьи и метаданные источника (provenance).")
    public Function<ArticleLookupRequest, String> findArticle(GraphServiceClient graphServiceClient) {
        return request -> {
            log.info("Tool findArticle called: {} ст. {} ({})", request.code(), request.number(), request.country());
            String country = request.country() == null ? "RK" : request.country();

            try {
                ArticleInfo article = graphServiceClient.getArticle(request.code(), request.number(), country);
                if (article == null) {
                    return "Статья " + request.number() + " закона " + request.code() + " не найдена.";
                }

                StringBuilder sb = new StringBuilder();
                sb.append("**Ст. ").append(article.number()).append(" ").append(article.lawCode());
                if (article.title() != null) sb.append(" — ").append(article.title());
                sb.append("**\n\n");
                if (article.body() != null) sb.append(article.body()).append("\n\n");
                if (article.source() != null) {
                    sb.append("_Источник: ").append(article.source());
                    if (article.confidence() != null) sb.append(", confidence=").append(article.confidence());
                    if (article.verifiedBy() != null) sb.append(", verified by ").append(article.verifiedBy());
                    sb.append("_");
                }
                return sb.toString();
            } catch (Exception e) {
                log.warn("Failed to retrieve article {} for law {}: {}", request.number(), request.code(), e.getMessage());
                return "Ошибка при получении статьи " + request.number() + " закона " + request.code() + ".";
            }
        };
    }

    @Bean
    @Description("Полнотекстовый поиск по статьям законов. Возвращает релевантные статьи с указанием закона. " +
            "query — текст/тема для поиска, country — RK или RF.")
    public Function<ArticleSearchRequest, String> searchArticles(GraphServiceClient graphServiceClient) {
        return request -> {
            log.info("Tool searchArticles called: query='{}' country={}", request.query(), request.country());
            String country = request.country() == null ? "RK" : request.country();

            try {
                List<ArticleInfo> articles = graphServiceClient.searchArticles(request.query(), country);
                if (articles == null || articles.isEmpty()) {
                    return "Ничего не найдено по запросу «" + request.query() + "» среди статей.";
                }
                return articles.stream()
                        .limit(10)
                        .map(a -> "- **" + a.lawCode() + " ст. " + a.number() + "**" + (a.title() != null ? ": " + a.title() : ""))
                        .collect(Collectors.joining("\n"));
            } catch (Exception e) {
                log.warn("Failed to search articles for query '{}': {}", request.query(), e.getMessage());
                return "Ошибка при поиске статей по запросу.";
            }
        };
    }

    @Bean
    @Description("Связать конкретный пункт пользовательского документа с конкретной статьёй закона (clause-anchoring). " +
            "Используй ПОСЛЕ того, как нашёл соответствующую статью и конкретный пункт документа. " +
            "graphId — активный граф, documentId — ID документа, lawCode + articleNumber — куда привязываем, " +
            "clauseRef — например 'п. 5.2', documentSnippet — точная цитата из документа, articleSnippet — цитата из статьи, " +
            "confidence — уверенность 0..1 (для прозрачности provenance).")
    public Function<LinkClauseToArticleRequest, String> linkDocumentClauseToArticle(GraphServiceClient graphServiceClient) {
        return request -> {
            log.info("Tool linkDocumentClauseToArticle: doc={} -> {} ст. {} (clause {})",
                    request.documentId(), request.lawCode(), request.articleNumber(), request.clauseRef());
            String country = request.country() == null ? "RK" : request.country();

            try {
                LinkClauseRequest body = new LinkClauseRequest(
                        request.graphId(),
                        request.documentId(),
                        request.lawCode(),
                        country,
                        request.articleNumber(),
                        request.clauseRef(),
                        request.documentSnippet(),
                        request.articleSnippet(),
                        "llm-brain",
                        request.confidence()
                );
                graphServiceClient.linkDocumentClauseToArticle(body);
                return "Привязан " + (request.clauseRef() == null ? "пункт документа" : request.clauseRef()) +
                        " к ст. " + request.articleNumber() + " закона " + request.lawCode() + ".";
            } catch (Exception e) {
                log.warn("Failed to link document clause to article: {}", e.getMessage());
                return "Не удалось привязать пункт к статье. Проверь существование статьи и документа в графе.";
            }
        };
    }

    @Bean
    @Description("Зафиксировать конфликт между двумя статьями законов в текущем графе. " +
            "Используй когда обнаружил противоречие или коллизию норм между двумя статьями. " +
            "graphId — активный граф, codeA/numberA — первая статья (закон+номер), codeB/numberB — вторая, " +
            "reason — краткое объяснение конфликта на русском, confidence — уверенность 0..1.")
    public Function<FlagArticleConflictRequest, String> flagArticleConflict(SituationClient situationClient) {
        return request -> {
            log.info("Tool flagArticleConflict: {}ст.{} vs {}ст.{} graph={}",
                    request.codeA(), request.numberA(), request.codeB(), request.numberB(), request.graphId());
            String country = request.country() == null ? "RK" : request.country();

            try {
                FlagArticleConflictRequest body = new FlagArticleConflictRequest(
                        request.graphId(),
                        country,
                        request.codeA(),
                        request.numberA(),
                        request.codeB(),
                        request.numberB(),
                        request.reason(),
                        request.confidence()
                );
                situationClient.flagArticleConflict(body);
                return "Конфликт зафиксирован: " + request.codeA() + " ст." + request.numberA() +
                        " ↔ " + request.codeB() + " ст." + request.numberB() +
                        (request.reason() != null ? " — " + request.reason() : "");
            } catch (Exception e) {
                log.warn("Failed to flag article conflict: {}", e.getMessage());
                return "Не удалось зафиксировать конфликт — статьи не найдены в базе.";
            }
        };
    }

    @Bean
    @Description("Зафиксировать конфликт между пунктом пользовательского документа и статьёй закона. " +
            "Используй когда обнаружил, что пункт договора/иска противоречит конкретной статье закона. " +
            "graphId — активный граф, documentId — ID документа, lawCode + articleNumber — статья, " +
            "clauseRef — например 'п. 3.2', reason — объяснение конфликта, confidence — 0..1.")
    public Function<FlagDocumentArticleConflictRequest, String> flagDocumentArticleConflict(SituationClient situationClient) {
        return request -> {
            log.info("Tool flagDocumentArticleConflict: doc={} clause={} -> {}ст.{} graph={}",
                    request.documentId(), request.clauseRef(), request.lawCode(), request.articleNumber(), request.graphId());
            String country = request.country() == null ? "RK" : request.country();

            try {
                FlagDocumentArticleConflictRequest body = new FlagDocumentArticleConflictRequest(
                        request.graphId(),
                        request.documentId(),
                        request.lawCode(),
                        country,
                        request.articleNumber(),
                        request.clauseRef(),
                        request.reason(),
                        request.confidence()
                );
                situationClient.flagDocumentArticleConflict(body);
                return "Конфликт зафиксирован: документ (" + (request.clauseRef() == null ? "?" : request.clauseRef()) +
                        ") ↔ " + request.lawCode() + " ст." + request.articleNumber() +
                        (request.reason() != null ? " — " + request.reason() : "");
            } catch (Exception e) {
                log.warn("Failed to flag document-article conflict: {}", e.getMessage());
                return "Не удалось зафиксировать конфликт — документ или статья не найдены.";
            }
        };
    }

    @Bean
    @Description("Найти законы, семантически связанные с загруженным пользователем документом, и соединить их с ним в графе. " +
            "Используется когда юрист загружает свой документ (договор, иск) и хочет понять, какие законы РК с ним связаны. " +
            "graphId — активный граф, documentId — ID документа уже в графе, userId — ID пользователя, limit — сколько связей создать (по умолчанию 5).")
    public Function<LinkDocumentToLawsRequest, String> linkDocumentToLawsInGraph(GraphActionService graphActionService) {
        return request -> {
            log.info("Tool linkDocumentToLawsInGraph: graphId={}, doc={}", request.graphId(), request.documentId());
            int limit = request.limit() == null ? 5 : request.limit();
            var result = graphActionService.linkDocumentToLaws(
                    request.graphId(),
                    request.documentId(),
                    request.userId(),
                    limit,
                    "RK"
            );
            return result.report();
        };
    }
}