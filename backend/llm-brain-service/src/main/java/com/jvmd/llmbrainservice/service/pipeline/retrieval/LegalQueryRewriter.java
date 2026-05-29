package com.jvmd.llmbrainservice.service.pipeline.retrieval;

import com.jvmd.llmbrainservice.util.TextMatcher;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LegalQueryRewriter {

    private static final List<String> CONVERSATIONAL_FILLER = List.of(
        "привет",
        "пожалуйста",
        "спасибо",
        "пока",
        "давай",
        "окей",
        "скажи мне",
        "расскажи",
        "расскажи мне",
        "объясни",
        "объясни мне",
        "что такое",
        "как это",
        "помоги",
        "помогите",
        "дай мне",
        "можешь ли",
        "сможешь ли",
        "можно ли",
        "может ли"
    );

    private static final List<String> LAW_TERMS = List.of(
        "закон",
        "законы",
        "законодательство",
        "нпа",
        "нормативный",
        "нормативные",
        "статья",
        "статьи",
        "норма",
        "нормы",
        "кодекс",
        "гк",
        "ук",
        "коап",
        "тк",
        "налоговый кодекс",
        "гражданский кодекс",
        "трудовой кодекс",
        "правило",
        "правила",
        "постановление",
        "приказ",
        "регламент",
        "судебная практика",
        "практика суда",
        "правовая позиция",
        "санкция",
        "штраф",
        "ответственность",
        "требования закона",
        "правомерно",
        "законно",
        "незаконно",
        "соответствует закону",
        "развод",
        "алименты",
        "брак",
        "ребенок",
        "дети",
        "опека",
        "аренда",
        "недвижимость",
        "квартира",
        "земля",
        "ипотека",
        "договор",
        "преступление",
        "компания"
    );

    private static final List<String> COMPARISON_TERMS = List.of(
        "сравни",
        "сравнить",
        "сопоставь",
        "сопоставить",
        "соответствует",
        "противоречит",
        "противоречие",
        "расхождение",
        "коллизия",
        "отличие"
    );

    private static final Pattern STATUTE_PATTERN = Pattern.compile(
        "(?:ст\\.|статья|статьи)\\s*\\d+",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ABBREVIATION_PATTERN = Pattern.compile(
        "\\b(?:ГК|ТК|СК|УК|НК|ЖК|ЗК|РК|РФ|КОАП)\\b"
    );

    public String rewrite(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return rawMessage;
        }

        String normalized = TextMatcher.normalize(rawMessage);
        String cleaned = removeConversationalFiller(normalized);
        String extracted = extractLegalContext(cleaned, normalized);
        String truncated = truncate(extracted, 100);

        log.debug("Query rewrite: '{}' → '{}'", rawMessage, truncated);
        return truncated;
    }

    private String removeConversationalFiller(String normalized) {
        String result = normalized;
        for (String filler : CONVERSATIONAL_FILLER) {
            result = result.replace(filler + " ", "");
            result = result.replace(" " + filler, "");
        }

        result = removeQuestionWordsAtStart(result);
        return result.trim();
    }

    private String removeQuestionWordsAtStart(String normalized) {
        String[] questionWords = {
            "что ",
            "как ",
            "где ",
            "когда ",
            "кто ",
            "почему ",
            "зачем ",
            "сколько ",
        };
        for (String word : questionWords) {
            if (normalized.startsWith(word)) {
                return normalized.substring(word.length()).trim();
            }
        }
        return normalized;
    }

    private String extractLegalContext(String cleaned, String original) {
        int legalSignalCount = countLegalSignals(cleaned);

        if (legalSignalCount >= 2) {
            return cleaned;
        }

        String withContext = extractContextAroundLegalTerms(cleaned, original);
        return withContext.isEmpty() ? cleaned : withContext;
    }

    private int countLegalSignals(String normalized) {
        int count = 0;
        for (String term : LAW_TERMS) {
            if (normalized.contains(term)) count++;
        }
        for (String term : COMPARISON_TERMS) {
            if (normalized.contains(term)) count++;
        }
        if (STATUTE_PATTERN.matcher(normalized).find()) count++;
        if (ABBREVIATION_PATTERN.matcher(normalized).find()) count++;
        return count;
    }

    private String extractContextAroundLegalTerms(
        String cleaned,
        String original
    ) {
        StringBuilder context = new StringBuilder();
        String[] words = cleaned.split("\\s+");

        for (int i = 0; i < words.length; i++) {
            String word = words[i];

            if (isLegalSignal(word)) {
                int start = Math.max(0, i - 3);
                int end = Math.min(words.length, i + 4);

                for (int j = start; j < end; j++) {
                    if (context.length() > 0) context.append(" ");
                    context.append(words[j]);
                }
                context.append(" ");
            }
        }

        return context.toString().trim();
    }

    private boolean isLegalSignal(String word) {
        String lower = TextMatcher.normalize(word);
        if (TextMatcher.containsAny(lower, LAW_TERMS)) return true;
        if (TextMatcher.containsAny(lower, COMPARISON_TERMS)) return true;
        if (STATUTE_PATTERN.matcher(lower).find()) return true;
        return ABBREVIATION_PATTERN.matcher(word).find();
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        String truncated = text.substring(0, maxLength);
        int lastSpace = truncated.lastIndexOf(' ');
        if (lastSpace > maxLength / 2) {
            return truncated.substring(0, lastSpace);
        }

        return truncated;
    }
}
