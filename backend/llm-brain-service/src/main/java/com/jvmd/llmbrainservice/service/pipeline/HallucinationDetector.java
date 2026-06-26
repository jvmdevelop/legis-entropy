package com.jvmd.llmbrainservice.service.pipeline;

import com.jvmd.llmbrainservice.service.context.DocumentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class HallucinationDetector {

    private static final Pattern LAW_REFERENCE_PATTERN = Pattern.compile(
            "(?:стать[яиеею]|ст\\.?|закон|приказ|кодекс|постановление|положение|инструкция|" +
            "\\b[ГА]К (?:РК|РФ)|ТК (?:РК|РФ)|СК (?:РК|РФ)|ФЗ|РФ|ГК|УК|ПК|ЖК)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STATUTE_NUMBER_PATTERN = Pattern.compile(
            "(?:стать[яиеею]|статей|ст\\.?)\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern STATUTE_FULL_PATTERN = Pattern.compile(
            "((?:стать[яиеею]|статей|ст\\.?))\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE
    );

    public boolean hasGroundingContext(DocumentContext context) {
        return !retrievedArticleNumbers(context).isEmpty();
    }

    public boolean hasLawReferences(String answer) {
        return LAW_REFERENCE_PATTERN.matcher(answer.toLowerCase()).find();
    }


    public Set<Integer> getUngroundedArticles(String answer, DocumentContext context) {
        Set<Integer> ungrounded = new LinkedHashSet<>();
        if (answer == null || answer.isBlank()) return ungrounded;
        Set<Integer> retrieved = retrievedArticleNumbers(context);
        for (Integer num : extractArticleNumbers(answer)) {
            if (!retrieved.contains(num)) ungrounded.add(num);
        }
        return ungrounded;
    }

    public String redactUngroundedArticles(String answer, Set<Integer> ungrounded) {
        if (answer == null || answer.isBlank() || ungrounded == null || ungrounded.isEmpty()) {
            return answer;
        }
        Matcher m = STATUTE_FULL_PATTERN.matcher(answer);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String prefix = m.group(1);
            String numStr = m.group(2);
            try {
                int num = Integer.parseInt(numStr);
                if (ungrounded.contains(num)) {
                    m.appendReplacement(out, Matcher.quoteReplacement(
                            "**" + prefix + " [номер требует проверки — " + num + "]**"));
                } else {
                    m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
                }
            } catch (NumberFormatException e) {
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    private Set<Integer> extractArticleNumbers(String answer) {
        Set<Integer> articles = new LinkedHashSet<>();
        Matcher matcher = STATUTE_NUMBER_PATTERN.matcher(answer);
        while (matcher.find()) {
            try {
                articles.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {}
        }
        return articles;
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
