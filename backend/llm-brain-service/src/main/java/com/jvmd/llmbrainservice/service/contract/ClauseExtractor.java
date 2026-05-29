package com.jvmd.llmbrainservice.service.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ClauseExtractor {

    private static final Pattern CLAUSE_START = Pattern.compile(
        "(?m)^\\s*(?:п\\.?\\s*|пункт\\s+)?(\\d+(?:\\.\\d+)*)[\\.\\)]\\s+(\\S.{4,})",
        Pattern.CASE_INSENSITIVE
    );

    private static final int MAX_CLAUSE_TEXT = 400;
    private static final int MAX_CLAUSES = 50;

    public record Clause(String id, String text) {}

    public List<Clause> extract(String text) {
        if (text == null || text.isBlank()) return List.of();

        Matcher m = CLAUSE_START.matcher(text);
        List<int[]> starts = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        while (m.find() && starts.size() < MAX_CLAUSES) {
            starts.add(new int[] { m.start() });
            ids.add(m.group(1));
        }

        if (starts.isEmpty()) return List.of();

        List<Clause> clauses = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            int from = starts.get(i)[0];
            int to = (i + 1 < starts.size())
                ? starts.get(i + 1)[0]
                : text.length();
            String body = text.substring(from, to).trim();
            if (body.length() > MAX_CLAUSE_TEXT) body =
                body.substring(0, MAX_CLAUSE_TEXT).trim() + "…";
            clauses.add(new Clause(ids.get(i), body));
        }
        return clauses;
    }
}
