package com.jvmd.llmbrainservice.service.citation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvmd.llmbrainservice.service.llm.BrainModelClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReferenceAuditService {

    public enum Verdict { OK, WRONG_ARTICLE, NOT_FOUND, REPEALED, VIOLATION }

    public record Finding(String clause, String lawCode, String citedArticle, Verdict verdict,
                          String correctArticle, String explanation) {
        public boolean isProblem() { return verdict != Verdict.OK; }
    }

    public record Audit(List<Finding> findings, String summary, String markdown) {
        public static Audit empty() { return new Audit(List.of(), "", ""); }
        public long problems() { return findings.stream().filter(Finding::isProblem).count(); }
    }

    private static final java.util.regex.Pattern OK_ARTICLE =
            java.util.regex.Pattern.compile("\\s*(.+?)\\s+(?:ст\\.?\\s*)?(\\d+(?:-\\d+)?)\\s*");

    private final ArticleReferenceVerifier verifier;
    private final LaborNormChecker laborNormChecker;
    private final BrainModelClient modelClient;
    private final ObjectMapper objectMapper;

    public Audit audit(String documentText) {
        String report = verifier.buildReport(documentText);
        if (report.isBlank()) return Audit.empty();

        String prompt = """
                Сверка ссылок документа с официальными текстами законов РК:

                %s

                Найди ПРОБЛЕМНЫЕ ссылки. Правильный номер бери только из строк «по смыслу пункта подходит» или из оглавления кодекса.
                Верни ТОЛЬКО JSON:
                {"findings":[{"clause":"3.1","lawCode":"ТК РК","citedArticle":"49","verdict":"WRONG_ARTICLE","correctArticle":"36","explanation":"коротко"}],
                 "okArticles":["ТК РК 30","ТК РК 33"],
                 "summary":"2 предложения: сколько ссылок проверено, сколько проблем, что главное"}
                verdict: WRONG_ARTICLE — статья про другое; NOT_FOUND — статьи нет; REPEALED — акт утратил силу
                (lawCode = номер акта, citedArticle пусто); VIOLATION — условие пункта противоречит тексту статьи
                (например, срок меньше установленного законом) — отдельной записью. Корректные ссылки не включай
                в findings, перечисли их в okArticles как «код номер».
                """.formatted(report);

        String raw;
        try {
            raw = modelClient.complete(
                    "Ты — помощник юриста, который сверяет ссылки на нормы права. Отвечай строго JSON.", prompt);
        } catch (RuntimeException e) {
            log.warn("Reference audit LLM call failed: {}", e.getMessage());
            return new Audit(List.of(), "", "### Проверка ссылок документа\n\n" + report);
        }
        return withRules(parse(raw, report), documentText);
    }

    Audit withRules(Audit audit, String documentText) {
        List<LaborNormChecker.Violation> rules = laborNormChecker.check(documentText);
        java.util.Set<String> okKeys = new java.util.HashSet<>();
        for (Finding f : audit.findings()) if (f.verdict() == Verdict.OK) okKeys.add(f.lawCode() + "|" + f.citedArticle());

        List<Finding> kept = new ArrayList<>();
        for (Finding f : audit.findings()) {
            if (f.verdict() == Verdict.WRONG_ARTICLE && f.correctArticle().equals(f.citedArticle())) {
                okKeys.add(f.lawCode() + "|" + f.citedArticle());
                kept.add(new Finding(f.clause(), f.lawCode(), f.citedArticle(), Verdict.OK, "", ""));
                continue;
            }
            if (f.verdict() == Verdict.WRONG_ARTICLE && okKeys.contains(f.lawCode() + "|" + f.citedArticle())) continue;
            if (f.verdict() == Verdict.VIOLATION && f.correctArticle().equals(f.citedArticle())
                    && rules.stream().noneMatch(r -> r.correctArticle().equals(f.correctArticle()))) continue;
            if (f.verdict() == Verdict.OK && rules.stream().anyMatch(r -> r.citedArticle().equals(f.citedArticle()))) {
                continue;
            }
            kept.add(f);
        }
        for (LaborNormChecker.Violation r : rules) {
            kept.removeIf(f -> f.verdict() == Verdict.VIOLATION && f.correctArticle().equals(r.correctArticle()));
            kept.add(new Finding(r.clause(), "ТК РК", r.citedArticle(), Verdict.VIOLATION, r.correctArticle(), r.explanation()));
        }
        String summary = summarize(kept);
        return new Audit(List.copyOf(kept), summary, render(kept, summary));
    }

    static String summarize(List<Finding> findings) {
        long ok = findings.stream().filter(f -> f.verdict() == Verdict.OK).count();
        long wrong = findings.stream().filter(f -> f.verdict() == Verdict.WRONG_ARTICLE || f.verdict() == Verdict.NOT_FOUND).count();
        long repealed = findings.stream().filter(f -> f.verdict() == Verdict.REPEALED).count();
        long violations = findings.stream().filter(f -> f.verdict() == Verdict.VIOLATION).count();
        return "Проверено ссылок: " + (ok + wrong + repealed) + ". Корректных — " + ok + ", ошибочных — " + wrong
                + ", на утратившие силу акты — " + repealed + ". Нарушений закона по существу — " + violations + ".";
    }

    Audit parse(String raw, String fallbackReport) {
        try {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            JsonNode root = objectMapper.readTree(raw.substring(start, end + 1));
            List<Finding> findings = new ArrayList<>();
            for (JsonNode f : root.path("findings")) {
                Verdict verdict;
                try {
                    verdict = Verdict.valueOf(f.path("verdict").asText("OK").trim().toUpperCase());
                } catch (IllegalArgumentException e) {
                    continue;
                }
                findings.add(new Finding(
                        f.path("clause").asText(""),
                        f.path("lawCode").asText(""),
                        f.path("citedArticle").asText("").replaceAll("[^0-9-]", ""),
                        verdict,
                        f.path("correctArticle").asText("").replaceAll("[^0-9-]", ""),
                        f.path("explanation").asText("")));
            }
            for (JsonNode ok : root.path("okArticles")) {
                java.util.regex.Matcher m = OK_ARTICLE.matcher(ok.asText(""));
                if (m.matches()) findings.add(new Finding("", m.group(1).strip(), m.group(2), Verdict.OK, "", ""));
            }
            String summary = root.path("summary").asText("");
            return new Audit(findings, summary, render(findings, summary));
        } catch (Exception e) {
            log.warn("Could not parse reference audit JSON: {}", e.getMessage());
            return new Audit(List.of(), "", "### Проверка ссылок документа\n\n" + fallbackReport);
        }
    }

    private static String render(List<Finding> findings, String summary) {
        StringBuilder r = new StringBuilder("### Проверка ссылок документа\n\n");
        if (!summary.isBlank()) r.append(summary).append("\n\n");
        List<Finding> problems = findings.stream().filter(Finding::isProblem).toList();
        List<Finding> ok = findings.stream().filter(f -> !f.isProblem()).toList();
        for (Finding f : problems) {
            r.append("- ").append(icon(f.verdict())).append(" **").append(label(f.verdict())).append("**");
            if (!f.clause().isBlank()) r.append(" · п. ").append(f.clause());
            if (!f.citedArticle().isBlank()) r.append(" · ст. ").append(f.citedArticle()).append(' ').append(f.lawCode());
            else if (!f.lawCode().isBlank()) r.append(" · ").append(f.lawCode());
            if (!f.correctArticle().isBlank()) r.append(" → правильно: ст. ").append(f.correctArticle());
            if (!f.explanation().isBlank()) r.append("  \n  ").append(f.explanation());
            r.append('\n');
        }
        if (!ok.isEmpty()) {
            r.append("\nКорректные ссылки: ");
            r.append(String.join(", ", ok.stream()
                    .map(f -> "ст. " + f.citedArticle() + " " + f.lawCode()).distinct().toList()));
            r.append('\n');
        }
        return r.toString();
    }

    private static String icon(Verdict v) {
        return switch (v) {
            case VIOLATION -> "⛔";
            case REPEALED -> "🗑";
            case NOT_FOUND -> "❓";
            case WRONG_ARTICLE -> "⚠️";
            case OK -> "✅";
        };
    }

    private static String label(Verdict v) {
        return switch (v) {
            case VIOLATION -> "Нарушение закона";
            case REPEALED -> "Акт утратил силу";
            case NOT_FOUND -> "Статья не существует";
            case WRONG_ARTICLE -> "Ошибочная ссылка";
            case OK -> "Корректно";
        };
    }
}
