package com.jvmd.llmbrainservice.service.pipeline.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalQueryRewriterTest {

    private final LegalQueryRewriter rewriter = new LegalQueryRewriter();

    @Test
    void splitsQuestionListIntoSeparateQueries() {
        var queries = rewriter.rewriteAll(
            "Какую правовую природу имеет договор на разработку ПО? " +
            "Кому принадлежат исключительные права на служебное произведение? " +
            "Что грозит компании за нарушение локализации персональных данных?"
        );

        assertThat(queries).hasSize(3);
        assertThat(queries.get(2)).contains("персональных данных");
    }

    @Test
    void singleQuestionStaysSingleQuery() {
        assertThat(rewriter.rewriteAll("Какой срок исковой давности по договору займа?")).hasSize(1);
    }

    @Test
    void blankMessageYieldsOneQuery() {
        assertThat(rewriter.rewriteAll("  ")).hasSize(1);
    }
}
