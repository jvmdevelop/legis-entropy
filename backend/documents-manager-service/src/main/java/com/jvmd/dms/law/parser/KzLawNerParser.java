package com.jvmd.dms.law.parser;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class KzLawNerParser {

    private static final Pattern ARTICLE_REFERENCE = Pattern.compile("Статья\\s+(\\d+(?:\\.\\d+)?)(?:\\s+|-)(\\d+)?\\s*(?:Закона|Кодекса)?");
    private static final Pattern LAW_NAME_PATTERN = Pattern.compile("\"([^\"]+)\"\\s+(?:Закон|Кодекс|Закон\\s+Республики\\s+Казахстан)");
    private static final Pattern CODE_NAME_PATTERN = Pattern.compile("(Уголовный|Гражданский|Процессуальный|Налоговый|Трудовой)\\s+Кодекс");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})");
    private static final Pattern MONEY_PATTERN = Pattern.compile("(\\d+(?:\\s+\\d{3})*|\\d+[,.]\\d+)\\s*(тенге|млн|млрд|тыс)");
    private static final Pattern DOCUMENT_NUMBER = Pattern.compile("(\\d+)\\s*-\\s*([IVX]+)\\s*(?:ЗРК|КРК)");
    private static final Pattern PERSON_NAME = Pattern.compile("\\b([А-ЯЁ][а-яё]+\\s+[А-ЯЁ][а-яё]+(?:\\s+[А-ЯЁ][а-яё]+)?)\\b");

    public NerExtractionResult extractNamedEntities(String text) {
        NerExtractionResult result = new NerExtractionResult();

        if (text == null || text.isEmpty()) {
            return result;
        }

        extractArticleReferences(text, result);
        extractLawNames(text, result);
        extractCodeNames(text, result);
        extractDates(text, result);
        extractMoneyAmounts(text, result);
        extractDocumentNumbers(text, result);
        extractPersonNames(text, result);
        extractLegalTerms(text, result);

        return result;
    }

    private void extractArticleReferences(String text, NerExtractionResult result) {
        Matcher matcher = ARTICLE_REFERENCE.matcher(text);
        Set<String> seen = new HashSet<>();

        while (matcher.find()) {
            String articleRef = matcher.group(0).trim();
            if (seen.add(articleRef)) {
                int articleNumber = Integer.parseInt(matcher.group(1).split("\\.")[0]);
                result.articleReferences.add(new Entity("ARTICLE_REFERENCE", articleRef, articleNumber));
            }
        }
    }

    private void extractLawNames(String text, NerExtractionResult result) {
        Matcher matcher = LAW_NAME_PATTERN.matcher(text);
        while (matcher.find()) {
            String lawName = matcher.group(1).trim();
            result.lawNames.add(new Entity("LAW_NAME", lawName, lawName));
        }
    }

    private void extractCodeNames(String text, NerExtractionResult result) {
        Matcher matcher = CODE_NAME_PATTERN.matcher(text);
        while (matcher.find()) {
            String codeName = matcher.group(0).trim();
            result.codeNames.add(new Entity("CODE_NAME", codeName, codeName));
        }
    }

    private void extractDates(String text, NerExtractionResult result) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        while (matcher.find()) {
            String date = matcher.group(0);
            result.dates.add(new Entity("DATE", date, date));
        }
    }

    private void extractMoneyAmounts(String text, NerExtractionResult result) {
        Matcher matcher = MONEY_PATTERN.matcher(text);
        while (matcher.find()) {
            String amount = matcher.group(0).trim();
            result.moneyAmounts.add(new Entity("MONEY", amount, amount));
        }
    }

    private void extractDocumentNumbers(String text, NerExtractionResult result) {
        Matcher matcher = DOCUMENT_NUMBER.matcher(text);
        while (matcher.find()) {
            String docNum = matcher.group(0).trim();
            result.documentNumbers.add(new Entity("DOCUMENT_NUMBER", docNum, docNum));
        }
    }

    private void extractPersonNames(String text, NerExtractionResult result) {
        Matcher matcher = PERSON_NAME.matcher(text);
        Set<String> seen = new HashSet<>();

        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (seen.add(name) && !isCommonWord(name)) {
                result.personNames.add(new Entity("PERSON", name, name));
            }
        }
    }

    private void extractLegalTerms(String text, NerExtractionResult result) {
        String[] legalTerms = {
                "договор", "контракт", "соглашение", "нотариальное", "удостоверение",
                "ответственность", "штраф", "пени", "убытки", "компенсация",
                "обязательство", "право", "обязанность", "исковое требование",
                "иск", "истец", "ответчик", "третье лицо", "судебное решение",
                "апелляция", "кассация", "пересмотр", "законность", "обоснованность"
        };

        for (String term : legalTerms) {
            Pattern termPattern = Pattern.compile("\\b" + term + "\\b", Pattern.CASE_INSENSITIVE);
            Matcher matcher = termPattern.matcher(text);

            if (matcher.find()) {
                result.legalTerms.add(new Entity("LEGAL_TERM", term, term));
            }
        }
    }

    private boolean isCommonWord(String word) {
        String[] commonWords = {"Республика", "Казахстан", "Закона", "Статья", "Пункта", "Подпункта"};
        for (String common : commonWords) {
            if (word.equalsIgnoreCase(common)) {
                return true;
            }
        }
        return false;
    }

    @Data
    @AllArgsConstructor
    public static class Entity {
        private String type;
        private String text;
        private Object value;
    }

    @Data
    public static class NerExtractionResult {
        private List<Entity> articleReferences = new ArrayList<>();
        private List<Entity> lawNames = new ArrayList<>();
        private List<Entity> codeNames = new ArrayList<>();
        private List<Entity> dates = new ArrayList<>();
        private List<Entity> moneyAmounts = new ArrayList<>();
        private List<Entity> documentNumbers = new ArrayList<>();
        private List<Entity> personNames = new ArrayList<>();
        private List<Entity> legalTerms = new ArrayList<>();

        public int getTotalEntities() {
            return articleReferences.size() + lawNames.size() + codeNames.size() +
                    dates.size() + moneyAmounts.size() + documentNumbers.size() +
                    personNames.size() + legalTerms.size();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("articleReferences", articleReferences);
            map.put("lawNames", lawNames);
            map.put("codeNames", codeNames);
            map.put("dates", dates);
            map.put("moneyAmounts", moneyAmounts);
            map.put("documentNumbers", documentNumbers);
            map.put("personNames", personNames);
            map.put("legalTerms", legalTerms);
            map.put("totalEntities", getTotalEntities());
            return map;
        }
    }
}
