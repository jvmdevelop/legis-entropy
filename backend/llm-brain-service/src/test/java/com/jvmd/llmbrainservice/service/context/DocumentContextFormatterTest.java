package com.jvmd.llmbrainservice.service.context;

import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import com.jvmd.llmbrainservice.service.citation.CitationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentContextFormatterTest {

    private final DocumentContextFormatter formatter = new DocumentContextFormatter(new CitationService());

    @Test
    void returnsMissingDocumentMessageWhenDocumentsAreEmpty() {
        DocumentContext context = formatter.format(List.of());

        assertThat(context.text()).contains("не найден");
    }

    @Test
    void skipsBlankFragmentsAndFormatsNonBlankText() {
        RetrievalChunkResponse blank = chunk(" ", null);
        RetrievalChunkResponse document = chunk("Юридически значимый текст", 7);

        DocumentContext context = formatter.format(List.of(blank, document));

        assertThat(context.text())
                .contains("Фрагмент [doc-doc-1-chunk-7]")
                .contains("chunkIndex: 7")
                .contains("Юридически значимый текст")
                .doesNotContain("Фрагмент [chunk-1]");
        assertThat(context.citations()).hasSize(1);
    }

    @Test
    void limitsEachFragmentLength() {
        RetrievalChunkResponse document = chunk("a".repeat(2600), 1);

        DocumentContext context = formatter.format(List.of(document));

        assertThat(context.text()).contains("a".repeat(2500));
        assertThat(context.text()).doesNotContain("a".repeat(2501));
    }

    private RetrievalChunkResponse chunk(String text, Integer chunkIndex) {
        return new RetrievalChunkResponse(
                text,
                "doc-1",
                "contract.pdf",
                null,
                chunkIndex,
                0.91,
                Map.of("documentId", "doc-1")
        );
    }
}
