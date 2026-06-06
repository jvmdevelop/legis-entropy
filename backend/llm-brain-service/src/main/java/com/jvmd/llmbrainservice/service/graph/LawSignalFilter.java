package com.jvmd.llmbrainservice.service.graph;


import com.jvmd.llmbrainservice.dto.LawInfo;

import java.util.Locale;

public final class LawSignalFilter {

    private LawSignalFilter() {}

    public static boolean isLowSignal(LawInfo law) {
        String code = law.code() == null ? "" : law.code().toUpperCase(Locale.ROOT);
        if (code.endsWith(" РК") && code.length() <= 8) return false;
        String title = law.title() == null ? "" : law.title().toLowerCase(Locale.ROOT);
        if (title.isBlank()) return false;
        if (title.startsWith("о ратификации")) return true;
        if (title.startsWith("о денонсации")) return true;
        if (title.startsWith("о присоединении")) return true;
        if (title.contains("между республикой казахстан и")) return true;
        if (title.contains("между правительством республики казахстан")) return true;
        return title.startsWith("о подписании ");
    }
}
