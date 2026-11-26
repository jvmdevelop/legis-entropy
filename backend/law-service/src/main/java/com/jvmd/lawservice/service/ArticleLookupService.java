package com.jvmd.lawservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ArticleLookupService {

    public static final int EXCERPT_CHARS = 600;
    public static final int MAX_CHARS = 8000;

    private final JdbcTemplate jdbcTemplate;

    public record ArticleInfo(String lawCode, String articleNumber, String articleTitle, String excerpt) {}

    public record ArticleTitle(String articleNumber, String articleTitle) {}

    public List<ArticleInfo> lookup(String lawCode, Collection<String> numbers, String country) {
        return lookup(lawCode, numbers, country, EXCERPT_CHARS);
    }

    public List<ArticleInfo> lookup(String lawCode, Collection<String> numbers, String country, int maxChars) {
        int limit = Math.max(1, Math.min(maxChars, MAX_CHARS));
        if (lawCode == null || lawCode.isBlank() || numbers == null || numbers.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT metadata->>'articleNumber' AS num,
                       metadata->>'articleTitle'  AS title,
                       content
                FROM vector_store
                WHERE metadata->>'sourceType' = 'article'
                  AND metadata->>'country' = ?
                  AND metadata->>'lawCode' = ?
                  AND metadata->>'articleNumber' = ANY (string_to_array(?, ','))
                ORDER BY metadata->>'articleNumber',
                         COALESCE((metadata->>'chunk_index')::int, 0)
                """, normalizedCountry(country), lawCode, String.join(",", numbers));

        Map<String, String> titles = new LinkedHashMap<>();
        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String num = (String) row.get("num");
            boolean first = !bodies.containsKey(num);
            if (!first && limit <= EXCERPT_CHARS) continue;
            String title = (String) row.get("title");
            titles.putIfAbsent(num, title == null ? "" : title);
            StringBuilder sb = bodies.computeIfAbsent(num, k -> new StringBuilder());
            if (sb.length() < limit) sb.append(first ? "" : " ").append(body((String) row.get("content")));
        }
        List<ArticleInfo> out = new ArrayList<>();
        bodies.forEach((num, sb) -> out.add(new ArticleInfo(lawCode, num, titles.get(num), cut(sb.toString(), limit))));
        return out;
    }

    public List<ArticleTitle> titles(String lawCode, String country) {
        if (lawCode == null || lawCode.isBlank()) return List.of();
        List<ArticleTitle> titles = jdbcTemplate.query("""
                SELECT DISTINCT ON (metadata->>'articleNumber')
                       metadata->>'articleNumber' AS num,
                       metadata->>'articleTitle'  AS title
                FROM vector_store
                WHERE metadata->>'sourceType' = 'article'
                  AND metadata->>'country' = ?
                  AND metadata->>'lawCode' = ?
                ORDER BY metadata->>'articleNumber'
                """, (rs, i) -> new ArticleTitle(rs.getString("num"),
                        rs.getString("title") == null ? "" : rs.getString("title")),
                normalizedCountry(country), lawCode);
        return titles.stream().sorted(Comparator.comparing(ArticleLookupService::sortKey)).toList();
    }

    private static long sortKey(ArticleTitle title) {
        String[] parts = title.articleNumber().split("-", 2);
        try {
            long main = Long.parseLong(parts[0].trim());
            long sub = parts.length > 1 ? Long.parseLong(parts[1].replaceAll("\\D", "")) : 0;
            return main * 1000 + sub;
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private static String body(String content) {
        if (content == null) return "";
        String body = content;
        int newline = body.indexOf('\n');
        if (newline > 0 && newline < 300) body = body.substring(newline + 1);
        return body.strip().replaceAll("\\s+", " ");
    }

    private static String cut(String body, int limit) {
        return body.length() <= limit ? body : body.substring(0, limit) + "…";
    }

    private static String normalizedCountry(String country) {
        return country == null || country.isBlank() ? "RK" : country.toUpperCase();
    }
}
