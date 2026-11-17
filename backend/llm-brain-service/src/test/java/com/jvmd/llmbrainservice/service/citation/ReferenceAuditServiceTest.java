package com.jvmd.llmbrainservice.service.citation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceAuditServiceTest {

    private final ReferenceAuditService service = new ReferenceAuditService(null, new LaborNormChecker(), null, new ObjectMapper());

    @Test
    void parsesVerdictsEvenWhenWrappedInMarkdownFence() {
        String raw = """
                ```json
                {"findings":[
                  {"clause":"3.1","lawCode":"ТК РК","citedArticle":"ст. 49","verdict":"WRONG_ARTICLE","correctArticle":"36","explanation":"Ст. 49 — основания прекращения."},
                  {"clause":"6.1","lawCode":"ТК РК","citedArticle":"87","verdict":"violation","correctArticle":"88","explanation":"Отпуск 21 день меньше 24."},
                  {"clause":"x","lawCode":"ТК РК","citedArticle":"1","verdict":"MAYBE","correctArticle":"","explanation":""}
                ],"okArticles":["ТК РК 30","ГК РК ст. 172"],"summary":"Проверено 4 ссылки, 2 проблемы."}
                ```""";

        ReferenceAuditService.Audit audit = service.parse(raw, "fallback");

        assertThat(audit.findings()).hasSize(4);
        assertThat(audit.problems()).isEqualTo(2);
        assertThat(audit.findings().getFirst().citedArticle()).isEqualTo("49");
        assertThat(audit.markdown()).contains("Ошибочная ссылка", "правильно: ст. 36", "Нарушение закона",
                "Корректные ссылки: ст. 30 ТК РК, ст. 172 ГК РК");
    }

    @Test
    void fallsBackToRawReportOnGarbage() {
        assertThat(service.parse("модель ответила текстом", "СЫРОЙ ОТЧЁТ").markdown()).contains("СЫРОЙ ОТЧЁТ");
    }

    @Test
    void guardsOverrideContradictoryModelVerdictsAndAddRuleViolations() {
        String raw = """
                {"findings":[
                  {"clause":"7.1","lawCode":"ТК РК","citedArticle":"22","verdict":"WRONG_ARTICLE","correctArticle":"23","explanation":"x"},
                  {"clause":"5.2","lawCode":"ТК РК","citedArticle":"113","verdict":"VIOLATION","correctArticle":"113","explanation":"выдумано"}
                ],"okArticles":["ТК РК 22","ТК РК 87"],"summary":"старое"}""";
        String doc = "Трудовой договор\n6.1. Согласно статье 87 ТК РК отпуск не менее двадцати одного календарного дня.";

        ReferenceAuditService.Audit audit = service.withRules(service.parse(raw, "fb"), doc);

        assertThat(audit.findings()).extracting(ReferenceAuditService.Finding::verdict, ReferenceAuditService.Finding::citedArticle)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ReferenceAuditService.Verdict.OK, "22"),
                        org.assertj.core.groups.Tuple.tuple(ReferenceAuditService.Verdict.VIOLATION, "87"));
        assertThat(audit.summary()).contains("Нарушений закона по существу — 1").doesNotContain("старое");
    }

    @Test
    void wrongVerdictPointingToTheSameArticleBecomesOk() {
        String raw = """
                {"findings":[{"clause":"1.3","lawCode":"ТК РК","citedArticle":"33","verdict":"WRONG_ARTICLE","correctArticle":"33","explanation":"неточно"}],
                 "okArticles":[],"summary":""}""";
        ReferenceAuditService.Audit audit = service.withRules(service.parse(raw, "fb"), "Договор аренды");
        assertThat(audit.findings()).singleElement()
                .satisfies(f -> assertThat(f.verdict()).isEqualTo(ReferenceAuditService.Verdict.OK));
        assertThat(audit.markdown()).contains("Корректные ссылки: ст. 33 ТК РК").doesNotContain("Ошибочная");
    }
}
