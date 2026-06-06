package com.jvmd.lawservice.parser;

import com.jvmd.lawservice.model.kz.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class KzLawParser {

    private static final Pattern ARTICLE_PATTERN = Pattern.compile("^\\s*Статья\\s+(\\d+)\\s+(.+?)\\s*$", Pattern.MULTILINE);
    private static final Pattern CLAUSE_PATTERN = Pattern.compile("^\\s*(\\d+)\\.\\s+(.+?)$", Pattern.MULTILINE);
    private static final Pattern SUBCLAUSE_PATTERN = Pattern.compile("^\\s*([a-z)\\-\\d]+)\\s+(.+?)$", Pattern.MULTILINE);

    private static final Pattern CODE_ABBREVIATION = Pattern.compile("^(УК|ГК|ПК|НК|ТК|ТТК)\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAW_REFERENCE = Pattern.compile("Статья\\s+(\\d+)\\s+([А-Яа-яЁё\\s]+?)(?:\\sЗакона|\\sКодекса|\\s(?:Указа|Постановления|Приказа))?");
    private static final Pattern LAW_NUMBER_PATTERN = Pattern.compile("(\\d+)\\s*-\\s*([IVX]+)\\s*(?:ЗРК|КРК|ПРК)");

    public KzLegislativeDocument detectDocumentType(String title, String content) {
        KzLegislativeDocumentType type = inferDocumentType(title);
        return createDocumentByType(type);
    }

    public KzLegislativeDocumentType inferDocumentType(String title) {
        if (title == null) return KzLegislativeDocumentType.LAW;

        String lowerTitle = title.toLowerCase();

        if (lowerTitle.contains("конституция")) {
            return KzLegislativeDocumentType.CONSTITUTION;
        } else if (lowerTitle.matches(".*(?:уголовный|гражданский|процессуальный|налоговый|трудовой)\\s+кодекс.*")) {
            return KzLegislativeDocumentType.CODE;
        } else if (lowerTitle.contains("закон")) {
            return KzLegislativeDocumentType.LAW;
        } else if (lowerTitle.contains("указ") || lowerTitle.contains("указ президента")) {
            return KzLegislativeDocumentType.PRESIDENTIAL_DECREE;
        } else if (lowerTitle.contains("постановление") || lowerTitle.contains("постановление правительства")) {
            return KzLegislativeDocumentType.GOVERNMENT_RESOLUTION;
        } else if (lowerTitle.contains("приказ") || lowerTitle.contains("приказ министерства")) {
            return KzLegislativeDocumentType.MINISTERIAL_ORDER;
        }

        return KzLegislativeDocumentType.LAW;
    }

    public void parseArticlesAndClauses(KzLegislativeDocument document, String fullText) {
        if (fullText == null || fullText.isEmpty()) {
            log.warn("No text provided for document: {}", document.getTitle());
            return;
        }

        String[] lines = fullText.split("\n");
        Article currentArticle = null;
        Clause currentClause = null;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.isEmpty()) continue;

            Matcher articleMatcher = ARTICLE_PATTERN.matcher(trimmedLine);
            if (articleMatcher.matches()) {
                int articleNumber = Integer.parseInt(articleMatcher.group(1));
                String articleTitle = articleMatcher.group(2).trim();

                currentArticle = new Article();
                currentArticle.setArticleNumber(articleNumber);
                currentArticle.setTitle(articleTitle);
                currentArticle.setIsActive(true);
                document.addArticle(currentArticle);
                currentClause = null;
                continue;
            }

            if (currentArticle != null) {
                Matcher clauseMatcher = CLAUSE_PATTERN.matcher(trimmedLine);
                if (clauseMatcher.matches()) {
                    int clauseNumber = Integer.parseInt(clauseMatcher.group(1));
                    String clauseText = clauseMatcher.group(2).trim();

                    currentClause = new Clause();
                    currentClause.setClauseNumber(clauseNumber);
                    currentClause.setText(clauseText);
                    currentArticle.addClause(currentClause);
                    continue;
                }

                if (currentClause != null) {
                    Matcher subclauseMatcher = SUBCLAUSE_PATTERN.matcher(trimmedLine);
                    if (subclauseMatcher.matches()) {
                        String label = subclauseMatcher.group(1).trim();
                        String text = subclauseMatcher.group(2).trim();

                        Subclause subclause = new Subclause();
                        subclause.setLabel(label);
                        subclause.setText(text);
                        currentClause.addSubclause(subclause);
                        continue;
                    }

                    currentClause.setText(currentClause.getText() + " " + trimmedLine);
                }

                if (currentArticle.getFullText() == null) {
                    currentArticle.setFullText(trimmedLine);
                } else {
                    currentArticle.setFullText(currentArticle.getFullText() + " " + trimmedLine);
                }
            }
        }
    }

    public String extractCodeAbbreviation(String text) {
        if (text == null) return null;
        Matcher matcher = CODE_ABBREVIATION.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public LawReference extractLawReference(String text) {
        if (text == null) return null;

        Matcher matcher = LAW_REFERENCE.matcher(text);
        if (matcher.find()) {
            return new LawReference(
                    Integer.parseInt(matcher.group(1)),
                    matcher.group(2).trim()
            );
        }
        return null;
    }

    public String extractDocumentNumber(String text) {
        if (text == null) return null;
        Matcher matcher = LAW_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1) + "-" + matcher.group(2);
        }
        return null;
    }

    private KzLegislativeDocument createDocumentByType(KzLegislativeDocumentType type) {
        return switch (type) {
            case CONSTITUTION -> new Constitution();
            case CODE -> new Code();
            case LAW -> new SimpleLaw();
            case PRESIDENTIAL_DECREE -> new Regulation(Regulation.RegulationType.PRESIDENTIAL_DECREE);
            case GOVERNMENT_RESOLUTION -> new Regulation(Regulation.RegulationType.GOVERNMENT_RESOLUTION);
            case MINISTERIAL_ORDER -> new Regulation(Regulation.RegulationType.MINISTERIAL_ORDER);
            default -> new SimpleLaw();
        };
    }

    public static class LawReference {
        public final int articleNumber;
        public final String lawName;

        public LawReference(int articleNumber, String lawName) {
            this.articleNumber = articleNumber;
            this.lawName = lawName;
        }
    }
}
