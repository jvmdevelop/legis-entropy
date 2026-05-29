package com.jvmd.llmbrainservice.service.pipeline;

import com.jvmd.llmbrainservice.model.BrainRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    @Test
    void classifiesDocumentAuditWithoutLawContext() {
        ContextPlan plan = classify("Проверь договор и найди риски", "doc-1");

        assertThat(plan.taskType()).isEqualTo(BrainTaskType.DOCUMENT_AUDIT);
        assertThat(plan.retrievalMode()).isEqualTo(RetrievalMode.USER_DOCUMENT);
    }

    @Test
    void classifiesDocumentAuditAgainstLawAsHybrid() {
        ContextPlan plan = classify("Проверь договор на соответствие закону и найди нарушения", "doc-1");

        assertThat(plan.taskType()).isEqualTo(BrainTaskType.DOCUMENT_AUDIT);
        assertThat(plan.retrievalMode()).isEqualTo(RetrievalMode.HYBRID_LEGAL);
    }

    @Test
    void classifiesDocumentQuestionAsDocumentQa() {
        ContextPlan plan = classify("Где в документе указан срок оплаты?", "doc-1");

        assertThat(plan.taskType()).isEqualTo(BrainTaskType.DOCUMENT_QA);
        assertThat(plan.retrievalMode()).isEqualTo(RetrievalMode.USER_DOCUMENT);
    }

    @Test
    void classifiesLawSearchWithoutDocument() {
        ContextPlan plan = classify("Найди статьи ГК про расторжение договора", null);

        assertThat(plan.taskType()).isEqualTo(BrainTaskType.LAW_SEARCH);
        assertThat(plan.retrievalMode()).isEqualTo(RetrievalMode.LAW);
    }

    @Test
    void classifiesSummaryWithDocumentAsDocumentSummary() {
        ContextPlan plan = classify("Сделай краткое резюме документа", "doc-1");

        assertThat(plan.taskType()).isEqualTo(BrainTaskType.SUMMARY);
        assertThat(plan.retrievalMode()).isEqualTo(RetrievalMode.USER_DOCUMENT);
    }

    @Test
    void classifiesSummaryOfLawTopicAsLawBackedSummary() {
        ContextPlan plan = classify("Кратко перескажи нормы трудового кодекса про отпуск", null);

        assertThat(plan.taskType()).isEqualTo(BrainTaskType.SUMMARY);
        assertThat(plan.retrievalMode()).isEqualTo(RetrievalMode.LAW);
    }

    @Test
    void classifiesGeneralAdviceWithoutRetrieval() {
        ContextPlan plan = classify("Что делать, если контрагент задерживает оплату?", null);

        assertThat(plan.taskType()).isEqualTo(BrainTaskType.GENERAL_LEGAL_ADVICE);
        assertThat(plan.retrievalMode()).isEqualTo(RetrievalMode.NONE);
    }

    @Test
    void normalizesYoAndExtraSpaces() {
        ContextPlan plan = classify("  Поищи  всё про  законность условия  ", "doc-1");

        assertThat(plan.taskType()).isEqualTo(BrainTaskType.DOCUMENT_AUDIT);
        assertThat(plan.retrievalMode()).isEqualTo(RetrievalMode.HYBRID_LEGAL);
    }

    private ContextPlan classify(String message, String documentId) {
        return classifier.classify(new BrainRequest(message, "user-1", documentId, null, List.of()));
    }
}
