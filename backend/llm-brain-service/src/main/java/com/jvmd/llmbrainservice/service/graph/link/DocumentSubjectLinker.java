package com.jvmd.llmbrainservice.service.graph.link;

import com.jvmd.llmbrainservice.client.DmsClient;
import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.client.SituationClient;
import com.jvmd.llmbrainservice.dto.FlagDocumentArticleConflictRequest;
import com.jvmd.llmbrainservice.dto.LinkClauseRequest;
import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentSubjectLinker implements SubjectLinker {

    private final DmsClient dmsClient;
    private final GraphServiceClient graphServiceClient;
    private final SituationClient situationClient;

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
        return Optional.of(LinkableSubject.document(
            request.documentId(),
            Objects.requireNonNullElse(request.userId(), "")
        ));
    }

    @Override
    public String fetchSearchableText(LinkableSubject subject, String graphId) {
        String text = fetchFullText(subject);
        if (text != null && !text.isBlank()) return text;
        return fetchTextFromChunks(subject);
    }

    @Override
    public boolean linkToLaw(String graphId, LinkableSubject subject, String lawCode, String country) {
        try {
            graphServiceClient.linkDocumentToLaw(graphId, subject.id(), lawCode, country, "SEMANTIC");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean linkClauseToArticle(String graphId, LinkableSubject subject, String lawCode, String country,
                                       String articleNumber, String clauseRef,
                                       String subjectSnippet, String articleSnippet, double confidence) {
        try {
            graphServiceClient.linkDocumentClauseToArticle(new LinkClauseRequest(
                graphId, subject.id(), lawCode, country, articleNumber, clauseRef,
                subjectSnippet, articleSnippet, "deep-analysis", confidence));
            return true;
        } catch (Exception e) {
            log.warn("Failed to link document clause to article: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean flagConflictOnArticle(String graphId, LinkableSubject subject, String lawCode, String country,
                                         String articleNumber, String clauseRef, String reason, double confidence) {
        try {
            situationClient.flagDocumentArticleConflict(new FlagDocumentArticleConflictRequest(
                graphId, subject.id(), lawCode, country, articleNumber, clauseRef, reason, confidence));
            return true;
        } catch (Exception e) {
            log.warn("Failed to flag document-article conflict: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String displayName() { return "Документ"; }

    @Override
    public String displayNameAccusative() { return "документ"; }

    @Override
    public String displayNameGenitive() { return "документа"; }

    private String fetchFullText(LinkableSubject subject) {
        try {
            Map<String, String> resp = dmsClient.getDocumentText(subject.id(), subject.userId());
            if (resp != null) {
                return resp.getOrDefault("text", resp.get("plainText"));
            }
        } catch (Exception e) {
            log.debug("getDocumentText failed for {}: {}", subject.id(), e.getMessage());
        }
        return null;
    }

    private String fetchTextFromChunks(LinkableSubject subject) {
        try {
            List<RetrievalChunkResponse> chunks = dmsClient.searchUserDocuments("договор", subject.userId(), subject.id());
            if (chunks != null && !chunks.isEmpty()) {
                return chunks.stream().limit(5).map(RetrievalChunkResponse::text).collect(Collectors.joining(" "));
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
