package com.jvmd.llmbrainservice.service.pipeline;

import com.jvmd.llmbrainservice.service.citation.Citation;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HallucinationDetector {

    private static final Pattern LAW_REFERENCE_PATTERN = Pattern.compile(
            "(?:стать[яиеею]|ст\\.?|закон|приказ|кодекс|постановление|положение|инструкция|" +
            "\\b[ГА]К (?:РК|РФ)|ТК (?:РК|РФ)|СК (?:РК|РФ)|ФЗ|РФ|ГК|УК|ПК|ЖК)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STATUTE_FULL_PATTERN = Pattern.compile(
            "((?:стать[яиеею]|статей|ст\\.?))\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern LAW_CODE_PATTERN = Pattern.compile(
            "(?iu)\\b(?:УК|ГК|ТК|НК|СК|ЖК|ЗК|КоАП|ГПК|АППК)(?:\\s+(?:РК|РФ))?\\b"
    );

    public record UngroundedArticleReference(int number, String label, int start, int end) {}

    public boolean hasGroundingContext(DocumentContext context) {
        return !retrievedArticleNumbers(context).isEmpty();
    }

    public boolean hasLawReferences(String answer) {
        return answer != null && LAW_REFERENCE_PATTERN.matcher(answer).find();
    }


    public List<UngroundedArticleReference> getUngroundedArticleReferences(
            String answer,
            DocumentContext context
    ) {
        if (answer == null || answer.isBlank()) return List.of();
        List<UngroundedArticleReference> ungrounded = new ArrayList<>();
        Matcher matcher = STATUTE_FULL_PATTERN.matcher(answer);
        while (matcher.find()) {
            int number;
            try {
                number = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (!isGrounded(number, answer, matcher.start(), matcher.end(), context)) {
                ungrounded.add(new UngroundedArticleReference(number, matcher.group(1), matcher.start(), matcher.end()));
            }
        }
        return List.copyOf(ungrounded);
    }

    public String redactUngroundedArticleReferences(
            String answer,
            List<UngroundedArticleReference> ungrounded
    ) {
        if (answer == null || answer.isBlank() || ungrounded == null || ungrounded.isEmpty()) {
            return answer;
        }
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        for (UngroundedArticleReference reference : ungrounded) {
            if (reference.start() < cursor || reference.end() > answer.length()) continue;
            out.append(answer, cursor, reference.start());
            out.append("**").append(reference.label()).append(" [номер требует проверки — ")
                    .append(reference.number())
                    .append("]**");
            cursor = reference.end();
        }
        out.append(answer, cursor, answer.length());
        return out.toString();
    }

    private boolean isGrounded(
            int number,
            String answer,
            int start,
            int end,
            DocumentContext context
    ) {
        List<Citation> sameArticle = context == null || context.citations() == null
                ? List.of()
                : context.citations().stream()
                        .filter(citation -> citation.articleNumber() != null && citation.articleNumber() == number)
                        .toList();
        if (sameArticle.isEmpty()) return false;

        String explicitLawCode = nearbyLawCode(answer, start, end);
        if (explicitLawCode != null) {
            String normalizedMention = normalizeCode(explicitLawCode);
            return sameArticle.stream().anyMatch(citation -> citation.lawCode() != null
                    && codesMatch(normalizedMention, normalizeCode(citation.lawCode())));
        }

        long distinctLawCodes = sameArticle.stream()
                .map(citation -> normalizeCode(citation.lawCode()))
                .filter(code -> !code.isBlank())
                .distinct()
                .count();
        return distinctLawCodes <= 1;
    }

    private String nearbyLawCode(String answer, int start, int end) {
        int from = Math.max(0, start - 80);
        int to = Math.min(answer.length(), end + 80);
        String nearby = answer.substring(from, to);
        Matcher matcher = LAW_CODE_PATTERN.matcher(nearby);
        String nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        int localStart = start - from;
        while (matcher.find()) {
            int distance = matcher.end() <= localStart
                    ? localStart - matcher.end()
                    : Math.max(0, matcher.start() - localStart);
            if (distance < nearestDistance) {
                nearest = matcher.group();
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.toUpperCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private boolean codesMatch(String mention, String citation) {
        return mention.equals(citation) || mention.startsWith(citation) || citation.startsWith(mention);
    }

    private Set<Integer> retrievedArticleNumbers(DocumentContext context) {
        Set<Integer> retrieved = new HashSet<>();
        if (context == null || context.citations() == null) return retrieved;
        for (var citation : context.citations()) {
            if (citation.articleNumber() != null && citation.articleNumber() > 0) {
                retrieved.add(citation.articleNumber());
            }
        }
        return retrieved;
    }
}
