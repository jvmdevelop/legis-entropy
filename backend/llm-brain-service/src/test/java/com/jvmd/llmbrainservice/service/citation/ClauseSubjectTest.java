package com.jvmd.llmbrainservice.service.citation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClauseSubjectTest {

    @Test
    void stripsClauseNumberAndCitationKeepingTheSubject() {
        assertThat(ArticleReferenceVerifier.subjectOf(
                "3.1. В соответствии со статьёй 49 Трудового кодекса Республики Казахстан Стороны устанавливают испытательный срок продолжительностью два (2) месяца."))
                .isEqualTo("Стороны устанавливают испытательный срок продолжительностью два (2) месяца.");
        assertThat(ArticleReferenceVerifier.subjectOf(
                "5.3. Оплата сверхурочной работы производится в размере, установленном статьёй 114 Трудового кодекса Республики Казахстан."))
                .isEqualTo("Оплата сверхурочной работы производится в размере.");
        assertThat(ArticleReferenceVerifier.subjectOf(
                "10.3. Споры о восстановлении на работе рассматриваются судом в порядке, установленном статьёй 172 Гражданского кодекса Республики Казахстан (Особенная часть)."))
                .isEqualTo("Споры о восстановлении на работе рассматриваются судом.");
    }

    @Test
    void keepsClauseWhenNothingMeaningfulRemains() {
        String clause = "согласно ст. 30 ТК РК";
        assertThat(ArticleReferenceVerifier.subjectOf(clause)).isEqualTo(clause);
    }
}
