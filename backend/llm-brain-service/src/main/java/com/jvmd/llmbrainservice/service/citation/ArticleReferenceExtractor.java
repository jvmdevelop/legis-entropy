package com.jvmd.llmbrainservice.service.citation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ArticleReferenceExtractor {

    public record ArticleReference(String lawCode, String articleNumber, String clause, int position) {}

    private record CodeRule(String lawCode, Pattern fullName, Pattern abbreviation) {}

    private static final Pattern ARTICLE = Pattern.compile(
            "(?iu)(?<![\\p{L}])(?:стать[а-яё]*|ст\\.)\\s*(\\d+(?:-\\d+)?(?:\\s*(?:,|и)\\s*\\d+(?:-\\d+)?)*)");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:-\\d+)?");
    private static final Pattern NOT_A_CODE = Pattern.compile("(?iu)настоящ|(?<![\\p{L}])закон");

    private static final int LOOKAHEAD = 120;

    private static final List<CodeRule> CODES = List.of(
            rule("ГПК РК", "гражданск\\p{L}*\\s+процессуальн\\p{L}*\\s+кодекс", "ГПК"),
            rule("УПК РК", "уголовно[\\s-]*процессуальн\\p{L}*\\s+кодекс", "УПК"),
            rule("ТК РК", "трудов\\p{L}*\\s+кодекс", "ТК"),
            rule("ГК РК", "гражданск\\p{L}*\\s+кодекс", "ГК"),
            rule("НК РК", "налогов\\p{L}*\\s+кодекс", "НК"),
            rule("УК РК", "уголовн\\p{L}*\\s+кодекс", "УК"),
            rule("КоАП РК", "кодекс\\p{L}*[^.]{0,30}административных\\s+правонарушени", "КоАП"),
            rule("Земельный кодекс РК", "земельн\\p{L}*\\s+кодекс", "ЗК"),
            rule("ПК РК", "предпринимательск\\p{L}*\\s+кодекс", "ПК"),
            rule("Экологический кодекс РК", "экологическ\\p{L}*\\s+кодекс", "ЭК"),
            rule("Кодекс «О браке (супружестве) и семье»", "кодекс\\p{L}*[^.]{0,15}о\\s+браке", "КоБС")
    );

    private static CodeRule rule(String lawCode, String fullName, String abbreviation) {
        return new CodeRule(lawCode,
                Pattern.compile("(?iu)" + fullName),
                Pattern.compile("(?u)(?<![\\p{L}])" + abbreviation + "(?![\\p{L}])"));
    }

    public List<ArticleReference> extract(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<ArticleReference> refs = new ArrayList<>();
        Matcher m = ARTICLE.matcher(text);
        while (m.find()) {
            String lawCode = resolveCode(text, m.end());
            if (lawCode == null) continue;
            String clause = lineAround(text, m.start());
            Matcher numbers = NUMBER.matcher(m.group(1));
            while (numbers.find()) {
                refs.add(new ArticleReference(lawCode, numbers.group(), clause, m.start()));
            }
        }
        return refs;
    }

    String resolveCode(String text, int from) {
        String window = text.substring(from, Math.min(text.length(), from + LOOKAHEAD));
        int lineEnd = window.indexOf('\n');
        if (lineEnd >= 0) window = window.substring(0, lineEnd);

        String best = null;
        int bestPos = Integer.MAX_VALUE;
        for (CodeRule code : CODES) {
            for (Pattern p : List.of(code.fullName(), code.abbreviation())) {
                Matcher cm = p.matcher(window);
                if (cm.find() && cm.start() < bestPos) {
                    best = code.lawCode();
                    bestPos = cm.start();
                }
            }
        }
        if (best == null) return null;
        Matcher other = NOT_A_CODE.matcher(window);
        if (other.find() && other.start() < bestPos) return null;
        return best;
    }

    private static String lineAround(String text, int pos) {
        int start = text.lastIndexOf('\n', pos) + 1;
        int end = text.indexOf('\n', pos);
        if (end < 0) end = text.length();
        String line = text.substring(start, end).strip();
        return line.length() <= 400 ? line : line.substring(0, 400) + "…";
    }
}
