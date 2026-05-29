package com.jvmd.graphsservice.parser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CitationCodeBuilder {

    private static final Map<String, String> KZ_CODE_BY_TITLE =
        new LinkedHashMap<>();

    static {
        KZ_CODE_BY_TITLE.put("предприниматель", "ПК РК");
        KZ_CODE_BY_TITLE.put("уголовно-процессуальн", "УПК РК");
        KZ_CODE_BY_TITLE.put("уголовно-исполнительн", "УИК РК");
        KZ_CODE_BY_TITLE.put("гражданско-процессуальн", "ГПК РК");
        KZ_CODE_BY_TITLE.put("административн", "КоАП РК");
        KZ_CODE_BY_TITLE.put("уголовн", "УК РК");
        KZ_CODE_BY_TITLE.put("гражданск", "ГК РК");
        KZ_CODE_BY_TITLE.put("трудово", "ТК РК");
        KZ_CODE_BY_TITLE.put("налогов", "НК РК");
        KZ_CODE_BY_TITLE.put("бюджетн", "БК РК");
        KZ_CODE_BY_TITLE.put("брак", "Кодекс «О браке (супружестве) и семье»");
        KZ_CODE_BY_TITLE.put("семь", "Кодекс «О браке (супружестве) и семье»");
        KZ_CODE_BY_TITLE.put("земельн", "Земельный кодекс РК");
        KZ_CODE_BY_TITLE.put("водн", "Водный кодекс РК");
        KZ_CODE_BY_TITLE.put("лесн", "Лесной кодекс РК");
        KZ_CODE_BY_TITLE.put("экологическ", "Экологический кодекс РК");
        KZ_CODE_BY_TITLE.put("социальн", "Социальный кодекс РК");
        KZ_CODE_BY_TITLE.put("здоровь", "Кодекс «О здоровье народа»");
        KZ_CODE_BY_TITLE.put("таможен", "Таможенный кодекс РК");
        KZ_CODE_BY_TITLE.put("транспорт", "Транспортный кодекс РК");
    }

    public String build(
        String title,
        String formOfAct,
        String internalNumber,
        String country
    ) {
        if (!"RK".equalsIgnoreCase(country)) return null;
        if (title == null) title = "";
        String titleLower = title.toLowerCase(Locale.ROOT).replace('ё', 'е');

        String form = normalize(formOfAct);

        if (
            titleLower.startsWith("конституция республики казахстан") &&
            !form.contains("закон") &&
            !form.contains("постановление") &&
            !form.contains("решение") &&
            !form.contains("указ")
        ) {
            return "Конституция РК";
        }

        if (containsAny(form, "кодекс")) {
            for (Map.Entry<
                String,
                String
            > entry : KZ_CODE_BY_TITLE.entrySet()) {
                if (titleLower.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return (
                "Кодекс" +
                (internalNumber == null || internalNumber.isBlank()
                    ? ""
                    : " " + internalNumber)
            );
        }

        if (containsAny(form, "указ")) {
            return (
                "Указ Президента РК" +
                (internalNumber == null ? "" : " №" + internalNumber)
            );
        }
        if (containsAny(form, "постановление правительства", "постановление")) {
            return (
                "Постановление Правительства РК" +
                (internalNumber == null ? "" : " №" + internalNumber)
            );
        }
        if (containsAny(form, "приказ")) {
            return (
                "Приказ" + (internalNumber == null ? "" : " №" + internalNumber)
            );
        }
        if (containsAny(form, "закон")) {
            return (
                "Закон РК" +
                (internalNumber == null ? "" : " №" + internalNumber)
            );
        }

        return internalNumber == null || internalNumber.isBlank()
            ? null
            : internalNumber;
    }

    private static String normalize(String s) {
        return s == null
            ? ""
            : s.toLowerCase(Locale.ROOT).replace('ё', 'е').trim();
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }
}
