package com.jvmd.llmbrainservice.service.pipeline;

import com.jvmd.llmbrainservice.service.context.DocumentContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
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

        List<HallucinationDetector.UngroundedArticleReference> ungrounded =
                hallucinationDetector.getUngroundedArticleReferences(stripped, context);
        if (!ungrounded.isEmpty()) {
            List<Integer> numbers = ungrounded.stream()
                    .map(HallucinationDetector.UngroundedArticleReference::number)
                    .distinct()
                    .toList();
            log.warn("Redacting {} ungrounded article reference(s): {}", ungrounded.size(), numbers);
            String redacted = hallucinationDetector.redactUngroundedArticleReferences(stripped, ungrounded);
            String list = numbers.stream().map(String::valueOf).collect(Collectors.joining(", "));
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
