package com.jvmd.llmbrainservice.util;

import java.util.List;
import java.util.Locale;

public final class TextMatcher {

    private TextMatcher() {}

    public static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ").trim();
    }

    public static boolean containsAny(String text, List<String> terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }
}
