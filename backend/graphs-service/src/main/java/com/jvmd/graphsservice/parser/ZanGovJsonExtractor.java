package com.jvmd.graphsservice.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.jvmd.graphsservice.model.Article;
import com.jvmd.graphsservice.model.Provenance;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ZanGovJsonExtractor {

    private static final Pattern INLINE_ARTICLE_TITLE = Pattern.compile(
        "^\\s*Статья\\s+\\d+(?:-\\d+)?[.\\s]+(.*)$",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern AMENDMENT_DATE_NUM = Pattern.compile(
        "от\\s+(\\d{2}\\.\\d{2}\\.\\d{4})\\s+(?:[А-Яа-яёЁ]+\\s+РК\\s+)?№\\s*([\\w\\-]+(?:\\s+[A-ZА-ЯЁ\\-]+)?)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EFFECTIVE_FROM = Pattern.compile(
        "вводится\\s+в\\s+действие\\s+(?:с|по\\s+истечении|по)\\s+(\\d{2}\\.\\d{2}\\.\\d{4})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ARTICLE_REF_IN_TEXT = Pattern.compile(
        "стат(?:ье|ьи|ью|ьей|ей)\\s+(\\d+(?:-\\d+)?)",
        Pattern.CASE_INSENSITIVE
    );

    public LawStructure.ExtractionResult extract(
        JsonNode docJson,
        String lawCode,
        String country,
        String sourceUri
    ) {
        JsonNode content = docJson.path("content");
        if (!content.isArray() || content.size() == 0) {
            log.debug("ZanGovJsonExtractor: no content[] for {}", lawCode);
            return new LawStructure.ExtractionResult(
                List.of(),
                List.of(),
                List.of()
            );
        }

        String currentPart = null;
        String currentSectionNumber = null;
        String currentSectionTitle = null;
        String currentChapterNumber = null;
        String currentChapterTitle = null;

        String pendingHierarchy = null;
        String pendingHierarchyIndex = null;

        LinkedHashMap<String, Article> articlesByNumber = new LinkedHashMap<>();
        List<LawStructure.AmendmentEdge> amendments = new ArrayList<>();
        List<LawStructure.CrossRefEdge> crossRefs = new ArrayList<>();

        Article currentArticle = null;
        StringBuilder currentBody = null;
        boolean articleTitlePending = false;
        LocalDateTime now = LocalDateTime.now();

        for (JsonNode node : content) {
            String type = node.path("type").asText("");
            String text = node.path("text").asText("").trim();
            String index = node.path("index").asText(null);

            switch (type) {
                case "part" -> {
                    pendingHierarchy = "part";
                    pendingHierarchyIndex = index;
                }
                case "section" -> {
                    finalizeArticle(currentArticle, currentBody);
                    currentArticle = null;
                    currentBody = null;
                    pendingHierarchy = "section";
                    pendingHierarchyIndex = index;
                }
                case "chapter" -> {
                    finalizeArticle(currentArticle, currentBody);
                    currentArticle = null;
                    currentBody = null;
                    pendingHierarchy = "chapter";
                    pendingHierarchyIndex = index;
                }
                case "article" -> {
                    finalizeArticle(currentArticle, currentBody);
                    String num =
                        index != null
                            ? index
                            : String.valueOf(articlesByNumber.size() + 1);
                    currentArticle = new Article(
                        lawCode,
                        country,
                        num,
                        null,
                        ""
                    );
                    currentArticle.setAnchor("z" + node.path("id").asText(""));
                    currentArticle.setPartTitle(currentPart);
                    currentArticle.setSectionNumber(currentSectionNumber);
                    currentArticle.setSectionTitle(currentSectionTitle);
                    currentArticle.setChapterNumber(currentChapterNumber);
                    currentArticle.setChapterTitle(currentChapterTitle);
                    currentArticle.setRecordedAt(now);
                    Provenance p = Provenance.govParser(sourceUri);
                    p.setExtractedAt(now);
                    currentArticle.applyProvenance(p);
                    articlesByNumber.put(num, currentArticle);
                    currentBody = new StringBuilder();
                    articleTitlePending = true;
                }
                case "heading", "subheading", "title" -> {
                    if (pendingHierarchy != null) {
                        String headingText = text;
                        String upper = headingText.toUpperCase();
                        if (upper.contains("ОБЩАЯ ЧАСТЬ")) headingText =
                            "ОБЩАЯ ЧАСТЬ";
                        else if (
                            upper.contains("ОСОБЕННАЯ ЧАСТЬ")
                        ) headingText = "ОСОБЕННАЯ ЧАСТЬ";
                        switch (pendingHierarchy) {
                            case "part" -> currentPart = headingText;
                            case "section" -> {
                                currentSectionNumber = pendingHierarchyIndex;
                                currentSectionTitle = headingText;
                            }
                            case "chapter" -> {
                                currentChapterNumber = pendingHierarchyIndex;
                                currentChapterTitle = headingText;
                            }
                        }
                        pendingHierarchy = null;
                        pendingHierarchyIndex = null;
                    } else if (articleTitlePending && currentArticle != null) {
                        String t = text.replaceFirst(
                            "(?i)^Статья\\s+\\d+(?:-\\d+)?[.\\s]*",
                            ""
                        );
                        currentArticle.setTitle(t.isBlank() ? null : t);
                        articleTitlePending = false;
                    } else if (currentArticle != null && !text.isBlank()) {
                        appendBody(currentBody, text);
                    }
                }
                case "text", "pre" -> {
                    if (currentArticle != null && !text.isBlank()) {
                        if (articleTitlePending) {
                            Matcher m = INLINE_ARTICLE_TITLE.matcher(text);
                            if (m.find()) {
                                String t = m.group(1).trim();
                                currentArticle.setTitle(t.isBlank() ? null : t);
                                articleTitlePending = false;
                                continue;
                            }
                        }
                        appendBody(currentBody, text);
                        collectCrossRefs(
                            text,
                            currentArticle.getNumber(),
                            crossRefs
                        );
                    }
                }
                case "point", "subpoint", "paragraph" -> {
                }
                case "note", "excluded" -> {
                    if (currentArticle == null) continue;
                    String byDocId = null;
                    String byInternalNumber = null;
                    for (JsonNode prop : node.path("properties")) {
                        String key = prop.path("key").asText("");
                        String value = prop.path("value").asText("");
                        if ("source".equals(key) && !value.isBlank()) byDocId =
                            value;
                        else if (
                            "sourceSummary".equals(key) && !value.isBlank()
                        ) {
                            byInternalNumber = extractAmendingNumber(value);
                        }
                    }
                    LocalDate amendmentDate = null;
                    LocalDate effectiveFrom = null;
                    Matcher dm = AMENDMENT_DATE_NUM.matcher(text);
                    if (dm.find()) {
                        amendmentDate = parseDmy(dm.group(1));
                        if (byInternalNumber == null) byInternalNumber = dm
                            .group(2)
                            .trim();
                    }
                    Matcher em = EFFECTIVE_FROM.matcher(text);
                    if (em.find()) effectiveFrom = parseDmy(em.group(1));
                    amendments.add(
                        new LawStructure.AmendmentEdge(
                            currentArticle.getNumber(),
                            byDocId,
                            byInternalNumber,
                            amendmentDate,
                            effectiveFrom,
                            null,
                            snippet(text, 280)
                        )
                    );
                    if (amendmentDate != null) {
                        LocalDate prev = currentArticle.getLastAmendmentDate();
                        if (prev == null || amendmentDate.isAfter(prev)) {
                            currentArticle.setLastAmendmentDate(amendmentDate);
                        }
                    }
                }
                default -> {
                }
            }
        }
        finalizeArticle(currentArticle, currentBody);

        log.info(
            "ZanGovJsonExtractor for {} ({}): {} articles, {} amendments, {} cross-refs",
            lawCode,
            country,
            articlesByNumber.size(),
            amendments.size(),
            crossRefs.size()
        );
        return new LawStructure.ExtractionResult(
            new ArrayList<>(articlesByNumber.values()),
            amendments,
            crossRefs
        );
    }

    private static void appendBody(StringBuilder body, String text) {
        if (body == null) return;
        if (body.length() > 0) body.append('\n');
        body.append(text);
    }

    private static void finalizeArticle(Article article, StringBuilder body) {
        if (article == null || body == null) return;
        String text = body.toString().trim();
        if (text.length() > 50_000) text = text.substring(0, 50_000);
        article.setBody(text);
    }

    private static void collectCrossRefs(
        String text,
        String fromArticleNumber,
        List<LawStructure.CrossRefEdge> out
    ) {
        Matcher m = ARTICLE_REF_IN_TEXT.matcher(text);
        while (m.find()) {
            String target = m.group(1);
            if (target.equals(fromArticleNumber)) continue;
            out.add(
                new LawStructure.CrossRefEdge(
                    fromArticleNumber,
                    null,
                    target,
                    snippet(text, 220)
                )
            );
        }
    }

    private static String extractAmendingNumber(String sourceSummary) {
        Matcher m = Pattern.compile(
            "№\\s*([\\w\\-]+(?:\\s+[A-ZА-ЯЁ\\-]+)?)"
        ).matcher(sourceSummary);
        if (m.find()) return m.group(1).trim();
        return null;
    }

    private static LocalDate parseDmy(String s) {
        if (s == null) return null;
        try {
            String[] parts = s.split("\\.");
            if (parts.length == 3) {
                return LocalDate.of(
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[0])
                );
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String snippet(String s, int max) {
        if (s == null) return "";
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }
}
