package com.jvmd.llmbrainservice.service.prompt;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrainPromptBuilderTest {

    private final BrainPromptBuilder builder = new BrainPromptBuilder(new LegalAssistantInstructions(), new ProfessionInstructions());

    @Test
    void includesRequestContextDocumentContextAndUserMessage() {
        BrainRequest request = new BrainRequest("Проверь договор", "user-1", "doc-7", null, null);
        com.jvmd.llmbrainservice.service.pipeline.ContextPlan plan = new com.jvmd.llmbrainservice.service.pipeline.ContextPlan(com.jvmd.llmbrainservice.service.pipeline.BrainTaskType.DOCUMENT_AUDIT, com.jvmd.llmbrainservice.service.pipeline.RetrievalMode.USER_DOCUMENT);

        String prompt = builder.buildUserPrompt(request, DocumentContext.of("Фрагмент договора", java.util.List.of()), plan, com.jvmd.llmbrainservice.service.pipeline.ProfessionProfile.GENERAL_LAWYER);

        assertThat(prompt)
                .contains("Проверь договор")
                .contains("Фрагмент договора");
    }

    @Test
    void usesReadablePlaceholderWhenDocumentIdIsAbsent() {
        BrainRequest request = new BrainRequest("Вопрос", "user-1", " ", null, null);
        com.jvmd.llmbrainservice.service.pipeline.ContextPlan plan = new com.jvmd.llmbrainservice.service.pipeline.ContextPlan(com.jvmd.llmbrainservice.service.pipeline.BrainTaskType.GENERAL_LEGAL_ADVICE, com.jvmd.llmbrainservice.service.pipeline.RetrievalMode.NONE);

        String prompt = builder.buildUserPrompt(request, DocumentContext.unavailable("Документ не прикреплен."), plan, com.jvmd.llmbrainservice.service.pipeline.ProfessionProfile.GENERAL_LAWYER);

        assertThat(prompt).contains("Вопрос");
    }
}
