package com.jvmd.llmbrainservice.service.graph.link;

import com.jvmd.llmbrainservice.client.DmsClient;
import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentSubjectLinker implements SubjectLinker {

    private final DmsClient dmsClient;
    private final GraphServiceClient graphServiceClient;

    @Override
    public SubjectKind kind() {
        return SubjectKind.DOCUMENT;
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean canHandle(BrainRequest request) {
        return request.hasDocumentId();
    }

    @Override
    public Optional<LinkableSubject> resolve(BrainRequest request) {
        if (!canHandle(request)) return Optional.empty();
        return Optional.of(
                LinkableSubject.document(
                        request.documentId(),
                        request.userId() == null ? "" : request.userId()
                )
        );
    }

    @Override
    public String fetchSearchableText(LinkableSubject subject, String graphId) {
        try {
            var resp = dmsClient.getDocumentText(
                    subject.id(),
                    subject.userId()
            );
            if (resp != null) {
                String text = resp.getOrDefault(
                        "text",
                        resp.getOrDefault("plainText", null)
                );
                if (text != null && !text.isBlank()) return text;
            }
        } catch (Exception e) {
            log.debug(
                    "getDocumentText failed for {}: {}",
                    subject.id(),
                    e.getMessage()
            );
        }
        try {
            List<RetrievalChunkResponse> chunks = dmsClient.searchUserDocuments(
                    "договор",
                    subject.userId(),
                    subject.id()
            );
            if (chunks != null && !chunks.isEmpty()) {
                return chunks
                        .stream()
                        .limit(5)
                        .map(RetrievalChunkResponse::text)
                        .collect(Collectors.joining(" "));
            }
        } catch (Exception ignored) {

        }
        return "";
    }

    @Override
    public boolean linkToLaw(
            String graphId,
            LinkableSubject subject,
            String lawCode,
            String country
    ) {

        try {
            graphServiceClient.linkDocumentToLaw(
                    graphId,
                    subject.id(),
                    lawCode,
                    country,
                    "SEMANTIC"
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean linkClauseToArticle(
            String graphId,
            LinkableSubject subject,
            String lawCode,
            String country,
            String articleNumber,
            String clauseRef,
            String subjectSnippet,
            String articleSnippet,
            double confidence
    ) {
        return graphServiceClient.linkDocumentClauseToArticle(
                graphId,
                subject.id(),
                lawCode,
                country,
                articleNumber,
                clauseRef,
                subjectSnippet,
                articleSnippet,
                "deep-analysis",
                confidence
        );
    }

    @Override
    public boolean flagConflictOnArticle(
            String graphId,
            LinkableSubject subject,
            String lawCode,
            String country,
            String articleNumber,
            String clauseRef,
            String reason,
            double confidence
    ) {
        return graphServiceClient.flagDocumentArticleConflict(
                graphId,
                subject.id(),
                lawCode,
                country,
                articleNumber,
                clauseRef,
                reason,
                confidence
        );
    }

    @Override
    public String displayName() {
        return "Документ";
    }

    @Override
    public String displayNameAccusative() {
        return "документ";
    }

    @Override
    public String displayNameGenitive() {
        return "документа";
    }
}
