package com.jvmd.graphsservice.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class CitationExtractor {

    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
        "ст(?:атья|атьи|атью|атьей|атьями|\\.|\\b)\\s*(\\d+(?:[-_]\\d+)?)",
        Pattern.CASE_INSENSITIVE
    );

    private static final int WINDOW = 120;

    private static final Map<String, String> KEY_TO_CODE;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("уголовно-процессуальн", "УПК РК");
        m.put("уголовно-исполнительн", "УИК РК");
        m.put("гражданско-процессуальн", "ГПК РК");
        m.put("предпринимательск", "ПК РК");
        m.put("административн", "КоАП РК");
        m.put("налоговый кодекс", "НК РК");
        m.put("гражданский кодекс", "ГК РК");
        m.put("трудовой кодекс", "ТК РК");
        m.put("уголовный кодекс", "УК РК");
        m.put("бюджетный кодекс", "БК РК");
        m.put("упк рк", "УПК РК");
        m.put("гпк рк", "ГПК РК");
        m.put("коап рк", "КоАП РК");
        m.put("нк рк", "НК РК");
        m.put("гк рк", "ГК РК");
        m.put("тк рк", "ТК РК");
        m.put("ук рк", "УК РК");
        m.put("пк рк", "ПК РК");
        m.put("бк рк", "БК РК");
        m.put("уик рк", "УИК РК");
        m.put("налогов", "НК РК");
        m.put("граждан", "ГК РК");
        m.put("трудов", "ТК РК");
        m.put("уголовн", "УК РК");
        m.put("предпринимат", "ПК РК");
        m.put("бюджетн", "БК РК");
        m.put("земельн", "Земельный кодекс РК");
        m.put("водн", "Водный кодекс РК");
        m.put("лесн", "Лесной кодекс РК");
        m.put("экологическ", "Экологический кодекс РК");
        m.put("социальн", "Социальный кодекс РК");
        m.put("семь", "Кодекс «О браке (супружестве) и семье»");
        m.put("брак", "Кодекс «О браке (супружестве) и семье»");
        m.put("таможен", "Таможенный кодекс РК");
        m.put("транспорт", "Транспортный кодекс РК");
        m.put("здоровь", "Кодекс «О здоровье народа»");
        KEY_TO_CODE = Map.copyOf(m);
    }

    public List<RawCitation> extract(String text) {
        if (text == null || text.isBlank()) return List.of();
        String norm = text.replace(' ', ' ');
        List<RawCitation> out = new ArrayList<>();
        Matcher m = ARTICLE_PATTERN.matcher(norm);
        while (m.find()) {
            String number = m.group(1).replace('_', '-');
            int from = Math.max(0, m.start() - WINDOW);
            int to = Math.min(norm.length(), m.end() + WINDOW);
            String window = norm.substring(from, to).toLowerCase(Locale.ROOT);
            String code = inferCode(window);
            String snippet = norm
                .substring(from, to)
                .replaceAll("\\s+", " ")
                .trim();
            out.add(new RawCitation(m.group(0), number, code, snippet));
        }
        return out;
    }

    private String inferCode(String windowLower) {
        for (Map.Entry<String, String> e : KEY_TO_CODE.entrySet()) {
            if (windowLower.contains(e.getKey())) return e.getValue();
        }
        return null;
    }

    public record RawCitation(
        String raw,
        String number,
        String inferredCode,
        String contextSnippet
    ) {}
}
