package com.jvmd.llmbrainservice.service.citation;

import com.jvmd.llmbrainservice.client.LawClient;
import com.jvmd.llmbrainservice.model.LawArticleInfo;
import com.jvmd.llmbrainservice.model.LawArticleTitle;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import com.jvmd.llmbrainservice.service.citation.ArticleReferenceExtractor.ArticleReference;
import com.jvmd.llmbrainservice.service.citation.RepealedActRegistry.RepealedMention;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArticleReferenceVerifier {

    private static final String COUNTRY = "RK";
    private static final int MAX_REFERENCES = 40;
    private static final int MAX_CLAUSE_SEARCHES = 20;
    private static final int TOC_MAX_ARTICLES = 300;
    private static final int TOC_MAX_LAWS = 2;
    private static final Duration TITLES_TTL = Duration.ofMinutes(30);

    private final LawClient lawClient;
    private final ArticleReferenceExtractor extractor;
    private final RepealedActRegistry repealedActs;
    private final LaborNormChecker laborNormChecker;

    private record CachedTitles(Map<String, String> titles, Instant loadedAt) {}

    private final Map<String, CachedTitles> titlesCache = new ConcurrentHashMap<>();

    public String buildReport(String documentText) {
        return buildReport(documentText, false);
    }

    public String buildReport(String documentText, boolean compact) {
        List<ArticleReference> refs = extractor.extract(documentText).stream().limit(MAX_REFERENCES).toList();
        List<RepealedMention> repealed = repealedActs.find(documentText);
        List<LaborNormChecker.Violation> violations = laborNormChecker.check(documentText);
        if (refs.isEmpty() && repealed.isEmpty() && violations.isEmpty()) return "";

        Map<String, Map<String, LawArticleInfo>> cited = lookupCited(refs);
        Map<String, List<RetrievalChunkResponse>> byMeaning = searchClauses(refs);

        StringBuilder sb = new StringBuilder();
        if (!compact) sb.append("""
                === ПРОВЕРКА ССЫЛОК ДОКУМЕНТА ПО БАЗЕ ЗАКОНОВ (выполнена автоматически по текстам с adilet.zan.kz) ===
                Как использовать: для каждой ссылки сравни, для чего статья использована в документе, с фактическим названием \
                и текстом статьи. Если предмет не совпадает — это ошибочная ссылка: укажи её в «Рисках» и назови правильную \
                статью (из блока «по смыслу» или из оглавления). Если статья не найдена — ссылка на несуществующую норму. \
                Если условие документа противоречит тексту статьи (сроки, суммы, продолжительность) — это нарушение закона, \
                укажи его отдельно. Совпадающие ссылки отметь как корректные, не помечай их как ошибки. \
                Номера статей из этого блока проверены — используй их без оговорок.

                """);

        if (!refs.isEmpty()) {
            sb.append("Ссылки в документе:\n");
            Set<String> seenClauseRefs = new LinkedHashSet<>();
            for (ArticleReference ref : refs) {
                if (!seenClauseRefs.add(ref.lawCode() + "|" + ref.articleNumber() + "|" + ref.clause())) continue;
                sb.append("- «").append(ref.clause()).append("»\n  → ")
                        .append(ref.lawCode()).append(" ст. ").append(ref.articleNumber()).append(": ");
                LawArticleInfo info = cited.getOrDefault(ref.lawCode(), Map.of()).get(ref.articleNumber());
                if (info != null) {
                    sb.append("фактически «").append(info.articleTitle()).append("»");
                    if (!compact) sb.append(". Начало текста: ").append(info.excerpt());
                    sb.append('\n');
                } else if (lawIndexed(ref.lawCode())) {
                    sb.append("СТАТЬЯ НЕ НАЙДЕНА в действующем тексте кодекса.\n");
                } else {
                    sb.append("кодекс отсутствует в базе платформы — проверить нельзя.\n");
                }
                List<RetrievalChunkResponse> similar = byMeaning.getOrDefault(ref.clause(), List.of());
                for (RetrievalChunkResponse hit : similar) {
                    sb.append("  по смыслу пункта подходит: ").append(describe(hit, compact ? 220 : 350)).append('\n');
                }
            }
        }

        if (!repealed.isEmpty()) {
            sb.append("\nСсылки на акты, УТРАТИВШИЕ СИЛУ (обязательно укажи в рисках):\n");
            for (RepealedMention mention : repealed) {
                sb.append("- ").append(mention.act().title()).append(" (№ ").append(mention.act().number())
                        .append(") — ").append(mention.act().status()).append(".\n  Пункт: «")
                        .append(mention.clause()).append("»\n");
            }
        }

        if (!violations.isEmpty()) {
            sb.append("\nНАРУШЕНИЯ НОРМ ТК РК (найдены автоматически, обязательно укажи в рисках):\n");
            for (LaborNormChecker.Violation v : violations) {
                sb.append("- п. ").append(v.clause()).append(": ").append(v.explanation())
                        .append(" Правильная норма — ст. ").append(v.correctArticle()).append(" ТК РК.\n");
            }
        }

        if (!compact) appendTablesOfContents(sb, refs);
        sb.append("=== КОНЕЦ ПРОВЕРКИ ССЫЛОК ===\n");
        return sb.toString();
    }

    public boolean exists(String lawCode, int number) {
        String code = canonicalCode(lawCode);
        return code != null && titles(code).containsKey(String.valueOf(number));
    }

    private Map<String, Map<String, LawArticleInfo>> lookupCited(List<ArticleReference> refs) {
        Map<String, Set<String>> numbersByLaw = new LinkedHashMap<>();
        for (ArticleReference ref : refs) {
            numbersByLaw.computeIfAbsent(ref.lawCode(), k -> new LinkedHashSet<>()).add(ref.articleNumber());
        }
        Map<String, Map<String, LawArticleInfo>> result = new LinkedHashMap<>();
        numbersByLaw.forEach((lawCode, numbers) -> {
            try {
                Map<String, LawArticleInfo> byNumber = new LinkedHashMap<>();
                for (LawArticleInfo info : lawClient.lookupArticles(lawCode, List.copyOf(numbers), COUNTRY)) {
                    byNumber.put(info.articleNumber(), info);
                }
                result.put(lawCode, byNumber);
            } catch (RuntimeException e) {
                log.warn("Article lookup failed for {} {}: {}", lawCode, numbers, e.getMessage());
            }
        });
        return result;
    }

    private Map<String, List<RetrievalChunkResponse>> searchClauses(List<ArticleReference> refs) {
        Map<String, String> lawByClause = new LinkedHashMap<>();
        for (ArticleReference ref : refs) {
            if (lawByClause.size() >= MAX_CLAUSE_SEARCHES) break;
            lawByClause.putIfAbsent(ref.clause(), ref.lawCode());
        }
        Map<String, CompletableFuture<List<RetrievalChunkResponse>>> futures = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            lawByClause.forEach((clause, lawCode) -> futures.put(clause, CompletableFuture.supplyAsync(() -> {
                try {
                    return lawClient.searchLawsByCountry(COUNTRY, subjectOf(clause)).stream()
                            .filter(hit -> lawCode.equals(meta(hit, "lawCode")))
                            .filter(hit -> meta(hit, "articleNumber") != null)
                            .collect(Collectors.toMap(hit -> meta(hit, "articleNumber"), hit -> hit,
                                    (a, b) -> a, LinkedHashMap::new))
                            .values().stream().limit(2).toList();
                } catch (RuntimeException e) {
                    log.debug("Clause search failed: {}", e.getMessage());
                    return List.<RetrievalChunkResponse>of();
                }
            }, executor)));
        }
        Map<String, List<RetrievalChunkResponse>> result = new LinkedHashMap<>();
        futures.forEach((clause, future) -> result.put(clause, future.join()));
        return result;
    }

    private static final java.util.regex.Pattern CLAUSE_NUMBER = java.util.regex.Pattern.compile("^\\s*\\d+(?:\\.\\d+)*\\.?\\s*");
    private static final java.util.regex.Pattern REFERENCE_PHRASE = java.util.regex.Pattern.compile(
            "(?iu)(?:в\\s+соответствии\\s+со?|согласно|в\\s+порядке,?\\s+(?:установленном|предусмотренном)|"
            + "(?:установленн|предусмотренн)\\p{L}*)?\\s*(?:стать\\p{L}*|ст\\.)\\s*\\d+(?:-\\d+)?"
            + "(?:\\s*(?:,|и)\\s*\\d+(?:-\\d+)?)*[^,.;]{0,60}?(?:кодекс\\p{L}*|(?<![\\p{L}])(?:ТК|ГК|НК|УК|ГПК|УПК|КоАП)(?![\\p{L}]))"
            + "(?:\\s+Республики\\s+Казахстан|\\s+РК)?(?:\\s*\\([^)]{0,40}\\))?");

    static String subjectOf(String clause) {
        String s = CLAUSE_NUMBER.matcher(clause).replaceFirst("");
        s = REFERENCE_PHRASE.matcher(s).replaceAll(" ");
        s = s.replaceAll("\\s+", " ").replaceAll("^[\\s,.;—-]+|[\\s,]+(?=[.;])", "").strip();
        return s.length() >= 15 ? s : clause;
    }

    private void appendTablesOfContents(StringBuilder sb, List<ArticleReference> refs) {
        List<String> laws = refs.stream().map(ArticleReference::lawCode).distinct().toList();
        int added = 0;
        for (String lawCode : laws) {
            if (added >= TOC_MAX_LAWS) break;
            Map<String, String> toc = titles(lawCode);
            if (toc.isEmpty() || toc.size() > TOC_MAX_ARTICLES) continue;
            sb.append("\nОглавление ").append(lawCode).append(" (для подбора правильных статей):\n");
            sb.append(toc.entrySet().stream()
                    .map(e -> "ст. " + e.getKey() + " — " + e.getValue())
                    .collect(Collectors.joining("; ")));
            sb.append('\n');
            added++;
        }
    }

    private boolean lawIndexed(String lawCode) {
        return !titles(lawCode).isEmpty();
    }

    private Map<String, String> titles(String lawCode) {
        CachedTitles cached = titlesCache.get(lawCode);
        if (cached != null && cached.loadedAt().plus(TITLES_TTL).isAfter(Instant.now())) {
            return cached.titles();
        }
        try {
            Map<String, String> titles = new LinkedHashMap<>();
            for (LawArticleTitle t : lawClient.articleTitles(lawCode, COUNTRY)) {
                titles.put(t.articleNumber(), Objects.requireNonNullElse(t.articleTitle(), ""));
            }
            titlesCache.put(lawCode, new CachedTitles(titles, Instant.now()));
            return titles;
        } catch (RuntimeException e) {
            log.warn("Could not load article titles for {}: {}", lawCode, e.getMessage());
            return cached != null ? cached.titles() : Map.of();
        }
    }

    private String canonicalCode(String lawCode) {
        if (lawCode == null || lawCode.isBlank()) return null;
        List<ArticleReference> refs = extractor.extract("ст. 1 " + lawCode);
        return refs.isEmpty() ? null : refs.getFirst().lawCode();
    }

    private static String describe(RetrievalChunkResponse hit, int maxChars) {
        String text = hit.text() == null ? "" : hit.text();
        int newline = text.indexOf('\n');
        if (newline > 0 && newline < 300) text = text.substring(newline + 1);
        text = text.strip().replaceAll("\\s+", " ");
        if (text.length() > maxChars) text = text.substring(0, maxChars) + "…";
        return meta(hit, "lawCode") + " ст. " + meta(hit, "articleNumber") + " «" + meta(hit, "articleTitle") + "»: " + text;
    }

    private static String meta(RetrievalChunkResponse hit, String key) {
        Object value = hit.metadata() == null ? null : hit.metadata().get(key);
        return value == null ? null : value.toString();
    }
}
