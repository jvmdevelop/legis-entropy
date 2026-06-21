package com.jvmd.llmbrainservice.service.prompt;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import com.jvmd.llmbrainservice.service.graph.link.SubjectLinkerRegistry;
import com.jvmd.llmbrainservice.service.pipeline.BrainTaskType;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.ProfessionProfile;
import com.jvmd.llmbrainservice.service.pipeline.RetrievalMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrainPromptBuilderTest {

    @Mock
    AssistantInstructions instructions;

    @Mock
    SubjectLinkerRegistry linkerRegistry;

    @InjectMocks
    BrainPromptBuilder builder;

    @Test
    void includesRequestContextDocumentContextAndUserMessage() {
        when(instructions.documentAnalysisInstruction()).thenReturn("Analyse document");
        when(instructions.generalAdviceInstruction()).thenReturn("Give advice");

        BrainRequest request = new BrainRequest("Проверь договор", "user-1", "doc-7", null, null);
        ContextPlan plan = new ContextPlan(BrainTaskType.DOCUMENT_AUDIT, RetrievalMode.USER_DOCUMENT);

        String prompt = builder.buildUserPrompt(request, DocumentContext.of("Фрагмент договора", List.of()), plan, ProfessionProfile.GENERAL_LAWYER);

        assertThat(prompt)
                .contains("Проверь договор")
                .contains("Фрагмент договора");
    }

    @Test
    void usesReadablePlaceholderWhenDocumentIdIsAbsent() {
        when(instructions.generalAdviceInstruction()).thenReturn("Give advice");

        BrainRequest request = new BrainRequest("Вопрос", "user-1", " ", null, null);
        ContextPlan plan = new ContextPlan(BrainTaskType.GENERAL_LEGAL_ADVICE, RetrievalMode.NONE);

        String prompt = builder.buildUserPrompt(request, DocumentContext.unavailable("Документ не прикреплен."), plan, ProfessionProfile.GENERAL_LAWYER);

        assertThat(prompt).contains("Вопрос");
    }
}
