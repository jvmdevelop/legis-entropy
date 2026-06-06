package com.jvmd.llmbrainservice.service.pipeline;

import com.jvmd.llmbrainservice.service.context.DocumentContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnswerPostProcessor {

    private final HallucinationDetector hallucinationDetector;

    public String process(String answer, ContextPlan plan, DocumentContext context) {
        if (answer == null || answer.isBlank()) {
            return "Не удалось получить ответ от LLM.";
        }
        String stripped = answer.strip();

        if (requiresCitations(plan) && context.hasCitations() && stripped.length() < 50) {
            return stripped + "\n\nПримечание: retrieval-контекст был найден, но ответ неполный. Проверьте вывод вручную.";
        }

        Set<Integer> ungrounded = hallucinationDetector.getUngroundedArticles(stripped, context);
        if (!ungrounded.isEmpty()) {
            log.warn("Redacting {} ungrounded article number(s): {}", ungrounded.size(), ungrounded);
            String redacted = hallucinationDetector.redactUngroundedArticles(stripped, ungrounded);
            String list = ungrounded.stream().map(String::valueOf).collect(Collectors.joining(", "));
            return redacted + "\n\n> Не удалось подтвердить номера статей по retrieval-контексту: **"
                    + list + "**. Перепроверьте по официальному источнику — модель могла перепутать раздел кодекса.";
        }

        return stripped;
    }

    private boolean requiresCitations(ContextPlan plan) {
        return plan.retrievalMode() == RetrievalMode.USER_DOCUMENT
                || plan.retrievalMode() == RetrievalMode.LAW
                || plan.retrievalMode() == RetrievalMode.HYBRID_LEGAL;
    }
}
