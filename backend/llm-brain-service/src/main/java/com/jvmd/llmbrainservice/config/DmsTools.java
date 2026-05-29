package com.jvmd.llmbrainservice.config;

import com.jvmd.llmbrainservice.client.DmsClient;
import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import com.jvmd.llmbrainservice.service.graph.GraphActionService;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

@Configuration
@Slf4j
public class DmsTools {

    public record LawSearchRequest(String query) {}

    public record UserDocSearchRequest(
        String query,
        String userId,
        String documentId
    ) {}

    public record LawGraphSearchRequest(String code, String country) {}

    public record LawRelatedSearchRequest(String code, String country) {}

    public record AddLawToGraphRequest(
        String graphId,
        String code,
        String country
    ) {}

    public record FindAndAddRelatedLawsRequest(
        String graphId,
        String query,
        String country,
        Integer limit
    ) {}

    public record LinkDocumentToLawsRequest(
        String graphId,
        String documentId,
        String userId,
        Integer limit
    ) {}

    public record ArticleLookupRequest(
        String code,
        String country,
        String number
    ) {}

    public record ArticleSearchRequest(String query, String country) {}

    public record LinkClauseToArticleRequest(
        String graphId,
        String documentId,
        String lawCode,
        String country,
        String articleNumber,
        String clauseRef,
        String documentSnippet,
        String articleSnippet,
        Double confidence
    ) {}

    public record FlagArticleConflictRequest(
        String graphId,
        String country,
        String codeA,
        String numberA,
        String codeB,
        String numberB,
        String reason,
        Double confidence
    ) {}

    public record FlagDocumentArticleConflictRequest(
        String graphId,
        String documentId,
        String lawCode,
        String country,
        String articleNumber,
        String clauseRef,
        String reason,
        Double confidence
    ) {}

    @Bean
    @Description(
        "Поиск в базе нормативно-правовых актов (законов) Казахстана и РФ"
    )
    public Function<LawSearchRequest, List<RetrievalChunkResponse>> searchLaws(
        DmsClient dmsClient
    ) {
        return request -> {
            log.info("Tool searchLaws called with query: {}", request.query());
            return dmsClient.searchLaws(request.query());
        };
    }

    @Bean
    @Description(
        "Поиск в загруженных пользователем документах (договорах, соглашениях)"
    )
    public Function<
        UserDocSearchRequest,
        List<RetrievalChunkResponse>
    > searchUserDocuments(DmsClient dmsClient) {
        return request -> {
            log.info(
                "Tool searchUserDocuments called for user {} with query: {}",
                request.userId(),
                request.query()
            );
            return dmsClient.searchUserDocuments(
                request.query(),
                request.userId(),
                request.documentId()
            );
        };
    }

    @Bean
    @Description(
        "Получить полную информацию о законе, включая его связи с другими нормативными актами. " +
            "code - краткое или полное название/код закона (например, 'ГК', 'Гражданский кодекс', '141-VII ЗРК'), " +
            "country - страна (RK или RF)"
    )
    public Function<LawGraphSearchRequest, String> getLawGraph(
        GraphServiceClient graphServiceClient
    ) {
        return request -> {
            log.info(
                "Tool getLawGraph called for {} in {}",
                request.code(),
                request.country()
            );

            var lawGraph = graphServiceClient.getLawWithRelationships(
                request.code(),
                request.country()
            );
            if (lawGraph.isPresent()) {
                return lawGraph
                    .map(GraphServiceClient.LawGraphResponse::formatForLLM)
                    .get();
            }

            log.debug(
                "Direct lookup failed for {}, attempting search fallback",
                request.code()
            );
            var searchResults = graphServiceClient.searchLaws(
                request.code(),
                request.country()
            );
            if (!searchResults.isEmpty()) {
                var firstResult = searchResults.get(0);
                log.info(
                    "Fallback search found law: {}",
                    firstResult.getCode()
                );

                var retryLawGraph = graphServiceClient.getLawWithRelationships(
                    firstResult.getCode(),
                    request.country()
                );
                if (retryLawGraph.isPresent()) {
                    return retryLawGraph
                        .map(GraphServiceClient.LawGraphResponse::formatForLLM)
                        .get();
                }
            }

            return (
                "Закон '" +
                request.code() +
                "' не найден в графе норм. Попробуйте уточнить название или код."
            );
        };
    }

    @Bean
    @Description(
        "Найти все законы, связанные с заданным законом (ссылаются на него, изменяют его, определяют его). " +
            "Полезно для понимания правового контекста и зависимостей."
    )
    public Function<LawRelatedSearchRequest, String> findRelatedLaws(
        GraphServiceClient graphServiceClient
    ) {
        return request -> {
            log.info(
                "Tool findRelatedLaws called for {} in {}",
                request.code(),
                request.country()
            );
            List<GraphServiceClient.LawInfo> laws =
                graphServiceClient.findRelatedLaws(
                    request.code(),
                    request.country()
                );
            if (laws.isEmpty()) {
                return "Не найдены законы, связанные с " + request.code() + ".";
            }
            return laws
                .stream()
                .map(
                    law ->
                        "- **" +
                        law.getCode() +
                        "**: " +
                        law.getTitle() +
                        (law.getSummary() != null
                            ? " (" + law.getSummary() + ")"
                            : "")
                )
                .collect(Collectors.joining("\n"));
        };
    }

    @Bean
    @Description(
        "Добавить закон в текущий граф пользователя (GraphView). " +
            "Параметр graphId должен совпадать с активным ID графа из системного контекста. " +
            "code — код или название закона, country — RK или RF (по умолчанию RK)."
    )
    public Function<AddLawToGraphRequest, String> addLawToUserGraph(
        GraphServiceClient graphServiceClient
    ) {
        return request -> {
            log.info(
                "Tool addLawToUserGraph called: graphId={}, code={}",
                request.graphId(),
                request.code()
            );
            String country =
                request.country() == null ? "RK" : request.country();
            boolean ok = graphServiceClient.addLawToUserGraph(
                request.graphId(),
                request.code(),
                country
            );
            if (!ok) {
                var found = graphServiceClient.searchLaws(
                    request.code(),
                    country
                );
                if (!found.isEmpty()) {
                    var first = found.get(0);
                    ok = graphServiceClient.addLawToUserGraph(
                        request.graphId(),
                        first.getCode(),
                        country
                    );
                    if (ok) return (
                        "Добавлен закон " +
                        first.getCode() +
                        ": " +
                        first.getTitle()
                    );
                }
                return (
                    "Не удалось добавить закон '" +
                    request.code() +
                    "' — не найден в базе."
                );
            }
            return "Закон " + request.code() + " добавлен в граф.";
        };
    }

    @Bean
    @Description(
        "Найти законы по смысловому запросу и автоматически добавить топ-K результатов в текущий граф пользователя. " +
            "Полезно когда юрист просит «найди все законы про защиту прав потребителей и добавь в граф». " +
            "graphId — ID активного графа из системного контекста, query — что искать, limit — сколько добавить (по умолчанию 5)."
    )
    public Function<
        FindAndAddRelatedLawsRequest,
        String
    > findAndAddRelatedLawsToGraph(GraphServiceClient graphServiceClient) {
        return request -> {
            log.info(
                "Tool findAndAddRelatedLawsToGraph called: graphId={}, query={}",
                request.graphId(),
                request.query()
            );
            String country =
                request.country() == null ? "RK" : request.country();
            int limit =
                request.limit() == null ? 5 : Math.min(request.limit(), 10);
            var laws = graphServiceClient.searchLaws(request.query(), country);
            if (laws.isEmpty()) {
                return (
                    "Ничего не найдено по запросу «" + request.query() + "»."
                );
            }
            int added = 0;
            StringBuilder report = new StringBuilder(
                "Найдено и добавлено в граф:\n"
            );
            for (var law : laws.stream().limit(limit).toList()) {
                if (
                    graphServiceClient.addLawToUserGraph(
                        request.graphId(),
                        law.getCode(),
                        country
                    )
                ) {
                    added++;
                    report
                        .append("- **")
                        .append(law.getCode())
                        .append("**: ")
                        .append(law.getTitle())
                        .append("\n");
                }
            }
            return added == 0
                ? "Ни один закон не удалось добавить в граф."
                : report.toString();
        };
    }

    @Bean
    @Description(
        "Получить конкретную статью закона по её номеру. " +
            "code — код закона (например, 'ГК'), number — номер статьи (например, '350'), country — RK или RF. " +
            "Возвращает заголовок, тело статьи и метаданные источника (provenance)."
    )
    public Function<ArticleLookupRequest, String> findArticle(
        GraphServiceClient graphServiceClient
    ) {
        return request -> {
            log.info(
                "Tool findArticle called: {} ст. {} ({})",
                request.code(),
                request.number(),
                request.country()
            );
            String country =
                request.country() == null ? "RK" : request.country();
            var article = graphServiceClient.getArticle(
                request.code(),
                request.number(),
                country
            );
            if (article.isEmpty()) {
                return (
                    "Статья " +
                    request.number() +
                    " закона " +
                    request.code() +
                    " не найдена."
                );
            }
            var a = article.get();
            StringBuilder sb = new StringBuilder();
            sb.append("**Ст. ")
                .append(a.getNumber())
                .append(" ")
                .append(a.getLawCode());
            if (a.getTitle() != null) sb.append(" — ").append(a.getTitle());
            sb.append("**\n\n");
            if (a.getBody() != null) sb.append(a.getBody()).append("\n\n");
            if (a.getSource() != null) {
                sb.append("_Источник: ").append(a.getSource());
                if (a.getConfidence() != null) sb.append(
                    ", confidence="
                ).append(a.getConfidence());
                if (a.getVerifiedBy() != null) sb.append(
                    ", verified by "
                ).append(a.getVerifiedBy());
                sb.append("_");
            }
            return sb.toString();
        };
    }

    @Bean
    @Description(
        "Полнотекстовый поиск по статьям законов. Возвращает релевантные статьи с указанием закона. " +
            "query — текст/тема для поиска, country — RK или RF."
    )
    public Function<ArticleSearchRequest, String> searchArticles(
        GraphServiceClient graphServiceClient
    ) {
        return request -> {
            log.info(
                "Tool searchArticles called: query='{}' country={}",
                request.query(),
                request.country()
            );
            String country =
                request.country() == null ? "RK" : request.country();
            var articles = graphServiceClient.searchArticles(
                request.query(),
                country
            );
            if (articles.isEmpty()) return (
                "Ничего не найдено по запросу «" +
                request.query() +
                "» среди статей."
            );
            return articles
                .stream()
                .limit(10)
                .map(
                    a ->
                        "- **" +
                        a.getLawCode() +
                        " ст. " +
                        a.getNumber() +
                        "**" +
                        (a.getTitle() != null ? ": " + a.getTitle() : "")
                )
                .collect(Collectors.joining("\n"));
        };
    }

    @Bean
    @Description(
        "Связать конкретный пункт пользовательского документа с конкретной статьёй закона (clause-anchoring). " +
            "Используй ПОСЛЕ того, как нашёл соответствующую статью и конкретный пункт документа. " +
            "graphId — активный граф, documentId — ID документа, lawCode + articleNumber — куда привязываем, " +
            "clauseRef — например 'п. 5.2', documentSnippet — точная цитата из документа, articleSnippet — цитата из статьи, " +
            "confidence — уверенность 0..1 (для прозрачности provenance)."
    )
    public Function<
        LinkClauseToArticleRequest,
        String
    > linkDocumentClauseToArticle(GraphServiceClient graphServiceClient) {
        return request -> {
            log.info(
                "Tool linkDocumentClauseToArticle: doc={} -> {} ст. {} (clause {})",
                request.documentId(),
                request.lawCode(),
                request.articleNumber(),
                request.clauseRef()
            );
            String country =
                request.country() == null ? "RK" : request.country();
            boolean ok = graphServiceClient.linkDocumentClauseToArticle(
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
            if (
                !ok
            ) return "Не удалось привязать пункт к статье. Проверь существование статьи и документа в графе.";
            return (
                "Привязан " +
                (request.clauseRef() == null
                    ? "пункт документа"
                    : request.clauseRef()) +
                " к ст. " +
                request.articleNumber() +
                " закона " +
                request.lawCode() +
                "."
            );
        };
    }

    @Bean
    @Description(
        "Зафиксировать конфликт между двумя статьями законов в текущем графе. " +
            "Используй когда обнаружил противоречие или коллизию норм между двумя статьями. " +
            "graphId — активный граф, codeA/numberA — первая статья (закон+номер), codeB/numberB — вторая, " +
            "reason — краткое объяснение конфликта на русском, confidence — уверенность 0..1."
    )
    public Function<FlagArticleConflictRequest, String> flagArticleConflict(
        GraphServiceClient graphServiceClient
    ) {
        return request -> {
            log.info(
                "Tool flagArticleConflict: {}ст.{} vs {}ст.{} graph={}",
                request.codeA(),
                request.numberA(),
                request.codeB(),
                request.numberB(),
                request.graphId()
            );
            String country =
                request.country() == null ? "RK" : request.country();
            boolean ok = graphServiceClient.flagArticleConflict(
                request.graphId(),
                country,
                request.codeA(),
                request.numberA(),
                request.codeB(),
                request.numberB(),
                request.reason(),
                request.confidence()
            );
            if (
                !ok
            ) return "Не удалось зафиксировать конфликт — статьи не найдены в базе.";
            return (
                "Конфликт зафиксирован: " +
                request.codeA() +
                " ст." +
                request.numberA() +
                " ↔ " +
                request.codeB() +
                " ст." +
                request.numberB() +
                (request.reason() != null ? " — " + request.reason() : "")
            );
        };
    }

    @Bean
    @Description(
        "Зафиксировать конфликт между пунктом пользовательского документа и статьёй закона. " +
            "Используй когда обнаружил, что пункт договора/иска противоречит конкретной статье закона. " +
            "graphId — активный граф, documentId — ID документа, lawCode + articleNumber — статья, " +
            "clauseRef — например 'п. 3.2', reason — объяснение конфликта, confidence — 0..1."
    )
    public Function<
        FlagDocumentArticleConflictRequest,
        String
    > flagDocumentArticleConflict(GraphServiceClient graphServiceClient) {
        return request -> {
            log.info(
                "Tool flagDocumentArticleConflict: doc={} clause={} -> {}ст.{} graph={}",
                request.documentId(),
                request.clauseRef(),
                request.lawCode(),
                request.articleNumber(),
                request.graphId()
            );
            String country =
                request.country() == null ? "RK" : request.country();
            boolean ok = graphServiceClient.flagDocumentArticleConflict(
                request.graphId(),
                request.documentId(),
                request.lawCode(),
                country,
                request.articleNumber(),
                request.clauseRef(),
                request.reason(),
                request.confidence()
            );
            if (
                !ok
            ) return "Не удалось зафиксировать конфликт — документ или статья не найдены.";
            return (
                "Конфликт зафиксирован: документ (" +
                (request.clauseRef() == null ? "?" : request.clauseRef()) +
                ") ↔ " +
                request.lawCode() +
                " ст." +
                request.articleNumber() +
                (request.reason() != null ? " — " + request.reason() : "")
            );
        };
    }

    @Bean
    @Description(
        "Найти законы, семантически связанные с загруженным пользователем документом, и соединить их с ним в графе. " +
            "Используется когда юрист загружает свой документ (договор, иск) и хочет понять, какие законы РК с ним связаны. " +
            "graphId — активный граф, documentId — ID документа уже в графе, userId — ID пользователя, limit — сколько связей создать (по умолчанию 5)."
    )
    public Function<
        LinkDocumentToLawsRequest,
        String
    > linkDocumentToLawsInGraph(GraphActionService graphActionService) {
        return request -> {
            log.info(
                "Tool linkDocumentToLawsInGraph: graphId={}, doc={}",
                request.graphId(),
                request.documentId()
            );
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
