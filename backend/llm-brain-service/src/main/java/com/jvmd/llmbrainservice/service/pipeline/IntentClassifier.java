package com.jvmd.llmbrainservice.service.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jvmd.llmbrainservice.model.BrainRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(
        IntentClassifier.class
    );
    private static final double CONFIDENCE_THRESHOLD = 0.70;

    private final RestClient intentClient;

    public IntentClassifier(
        @Value(
            "${intent.service.url:http://intent-service:8090}"
        ) String intentServiceUrl
    ) {
        this.intentClient = RestClient.builder()
            .baseUrl(intentServiceUrl)
            .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IntentResponse(String intent, double confidence) {}

    public ContextPlan classify(BrainRequest request) {
        if (
            !request.hasDocumentId() &&
            !request.hasSituationId() &&
            !request.hasVoiceId()
        ) {
            try {
                IntentResponse resp = intentClient
                    .post()
                    .uri("/classify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", request.message(), "lang", "ru"))
                    .retrieve()
                    .body(IntentResponse.class);
                if (resp != null && resp.confidence() >= CONFIDENCE_THRESHOLD) {
                    ContextPlan plan = mlIntentToPlan(resp.intent());
                    if (plan != null) {
                        log.debug(
                            "intent-service: {} ({:.2f})",
                            resp.intent(),
                            resp.confidence()
                        );
                        return plan;
                    }
                }
            } catch (Exception e) {
                log.warn(
                    "intent-service unavailable, using keyword fallback: {}",
                    e.getMessage()
                );
            }
        }

        String message = normalize(request.message());
        boolean hasDocument = request.hasDocumentId();

        boolean summary = containsAny(message, IntentKeywords.SUMMARY);
        boolean documentAudit = containsAny(
            message,
            IntentKeywords.DOCUMENT_AUDIT
        );
        boolean documentQa =
            containsAny(message, IntentKeywords.DOCUMENT_QA) ||
            looksLikeQuestion(message);
        boolean law = containsAny(message, IntentKeywords.LAW);
        boolean lawSearch = containsAny(message, IntentKeywords.LAW_SEARCH);
        boolean comparison = containsAny(message, IntentKeywords.COMPARISON);

        if (
            containsAny(message, IntentKeywords.DRAFT_ACTION) &&
            containsAny(message, IntentKeywords.DRAFT_TYPE)
        ) {
            return new ContextPlan(
                BrainTaskType.DOCUMENT_DRAFT,
                RetrievalMode.LAW
            );
        }

        if (
            containsAny(message, IntentKeywords.DEEP_ANALYSIS) ||
            (containsAny(
                    message,
                    List.of("проанализируй", "разбери", "анализируй")
                ) &&
                containsAny(
                    message,
                    List.of(
                        "глубок",
                        "идеальн",
                        "максимальн",
                        "тщательн",
                        "всесторон",
                        "исчерпыва",
                        "комплексн",
                        "детальн"
                    )
                ))
        ) {
            return new ContextPlan(
                BrainTaskType.GRAPH_DEEP_ANALYSIS,
                RetrievalMode.NONE
            );
        }
        boolean hasSubjectId =
            hasDocument || request.hasSituationId() || request.hasVoiceId();
        if (
            hasSubjectId &&
            (containsAny(message, IntentKeywords.LINK_SUBJECT) ||
                (containsAny(message, List.of("связ", "привяж", "соедин")) &&
                    containsAny(
                        message,
                        List.of(
                            "документ",
                            "договор",
                            "иск",
                            "норм",
                            "закон",
                            "ситуац",
                            "гс",
                            "голосов"
                        )
                    )))
        ) {
            return new ContextPlan(
                BrainTaskType.GRAPH_LINK_DOCUMENT,
                RetrievalMode.NONE
            );
        }
        if (
            containsAny(message, IntentKeywords.ADD_LAWS) ||
            (containsAny(
                    message,
                    List.of("добавь", "помести", "положи", "включи")
                ) && containsAny(message, List.of("граф", "в граф")))
        ) {
            return new ContextPlan(
                BrainTaskType.GRAPH_ADD_LAWS,
                RetrievalMode.NONE
            );
        }

        if (summary) {
            return new ContextPlan(
                BrainTaskType.SUMMARY,
                hasDocument
                    ? RetrievalMode.USER_DOCUMENT
                    : retrievalForLawOnly(law || lawSearch)
            );
        }

        if (
            hasDocument &&
            (comparison || (documentAudit && law) || (documentQa && lawSearch))
        ) {
            return new ContextPlan(
                BrainTaskType.DOCUMENT_AUDIT,
                RetrievalMode.HYBRID_LEGAL
            );
        }

        if (hasDocument && documentAudit) {
            return new ContextPlan(
                BrainTaskType.DOCUMENT_AUDIT,
                RetrievalMode.USER_DOCUMENT
            );
        }

        if (hasDocument && law && looksLikeLegalAdvice(message)) {
            return new ContextPlan(
                BrainTaskType.DOCUMENT_QA,
                RetrievalMode.HYBRID_LEGAL
            );
        }

        if (hasDocument && documentQa) {
            return new ContextPlan(
                BrainTaskType.DOCUMENT_QA,
                RetrievalMode.USER_DOCUMENT
            );
        }

        if (hasDocument) {
            return new ContextPlan(
                BrainTaskType.DOCUMENT_QA,
                RetrievalMode.USER_DOCUMENT
            );
        }

        if (law || lawSearch) {
            return new ContextPlan(BrainTaskType.LAW_SEARCH, RetrievalMode.LAW);
        }

        if (
            containsAny(message, IntentKeywords.GENERAL_ADVICE) ||
            looksLikeQuestion(message)
        ) {
            return new ContextPlan(
                BrainTaskType.GENERAL_LEGAL_ADVICE,
                RetrievalMode.NONE
            );
        }

        return new ContextPlan(
            BrainTaskType.GENERAL_LEGAL_ADVICE,
            RetrievalMode.NONE
        );
    }

    private ContextPlan mlIntentToPlan(String intent) {
        return switch (intent) {
            case "LAW_SEARCH" -> new ContextPlan(
                BrainTaskType.LAW_SEARCH,
                RetrievalMode.LAW
            );
            case "SUMMARY" -> new ContextPlan(
                BrainTaskType.SUMMARY,
                RetrievalMode.NONE
            );
            case "GENERAL_LEGAL_ADVICE" -> new ContextPlan(
                BrainTaskType.GENERAL_LEGAL_ADVICE,
                RetrievalMode.NONE
            );
            case "GRAPH_DEEP_ANALYSIS" -> new ContextPlan(
                BrainTaskType.GRAPH_DEEP_ANALYSIS,
                RetrievalMode.NONE
            );
            case "GRAPH_ADD_LAWS" -> new ContextPlan(
                BrainTaskType.GRAPH_ADD_LAWS,
                RetrievalMode.NONE
            );
            case "DOCUMENT_DRAFT" -> new ContextPlan(
                BrainTaskType.DOCUMENT_DRAFT,
                RetrievalMode.LAW
            );
            default -> null;
        };
    }

    private RetrievalMode retrievalForLawOnly(boolean lawRelated) {
        return lawRelated ? RetrievalMode.LAW : RetrievalMode.NONE;
    }

    public int classifyDepth(String message) {
        String normalized = normalize(message);
        if (containsAny(normalized, IntentKeywords.IDEAL_DEPTH)) {
            return 3;
        }
        return 2;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
            .toLowerCase(Locale.ROOT)
            .replace('ё', 'е')
            .replaceAll("\\s+", " ")
            .trim();
    }

    private boolean containsAny(String value, List<String> needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeQuestion(String message) {
        return (
            message.contains("?") ||
            startsWithAny(
                message,
                "что ",
                "как ",
                "когда ",
                "где ",
                "кто ",
                "почему ",
                "зачем ",
                "сколько "
            ) ||
            containsAny(
                message,
                List.of("объясни", "расскажи", "уточни", "поясни")
            )
        );
    }

    private boolean looksLikeLegalAdvice(String message) {
        return (
            containsAny(message, IntentKeywords.GENERAL_ADVICE) ||
            containsAny(
                message,
                List.of(
                    "правомерно",
                    "законно",
                    "незаконно",
                    "ответственность",
                    "штраф"
                )
            )
        );
    }

    private boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
