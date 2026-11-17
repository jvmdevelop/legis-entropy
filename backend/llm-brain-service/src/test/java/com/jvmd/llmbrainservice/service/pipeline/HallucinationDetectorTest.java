package com.jvmd.llmbrainservice.service.pipeline;

import com.jvmd.llmbrainservice.service.citation.ArticleReferenceVerifier;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HallucinationDetectorTest {

    @Mock
    ArticleReferenceVerifier verifier;

    @Test
    void articlesExistingInLawIndexAreGroundedEvenWithoutRetrievalHits() {
        when(verifier.exists(anyString(), anyInt())).thenReturn(false);
        when(verifier.exists("ТК РК", 36)).thenReturn(true);
        when(verifier.exists("ТК РК", 68)).thenReturn(true);
        HallucinationDetector detector = new HallucinationDetector(verifier);

        String answer = "Испытательный срок — ст. 36 ТК РК.\nСм. ст. 68 (рабочее время) и ст. 999 (выдумана) ТК РК.";
        var ungrounded = detector.getUngroundedArticleReferences(answer, DocumentContext.of("текст"));

        assertThat(ungrounded).extracting(HallucinationDetector.UngroundedArticleReference::number)
                .containsExactly(999);
        assertThat(ungrounded.getFirst().lawCode()).isEqualTo("ТК РК");
    }

    @Test
    void referencesWithoutCodeFallBackToTheCodeTheAnswerIsAbout() {
        when(verifier.exists(anyString(), anyInt())).thenReturn(false);
        when(verifier.exists("ТК РК", 93)).thenReturn(true);
        HallucinationDetector detector = new HallucinationDetector(verifier);

        String answer = "Договор ссылается на ТК РК и ТК РК.\nРабочее время (ссылки на ст. 93 неверны).\nСм. ст. 172 ГК РК.";
        var ungrounded = detector.getUngroundedArticleReferences(answer, DocumentContext.of("текст"));

        assertThat(ungrounded).extracting(HallucinationDetector.UngroundedArticleReference::number)
                .containsExactly(172);
    }
}
