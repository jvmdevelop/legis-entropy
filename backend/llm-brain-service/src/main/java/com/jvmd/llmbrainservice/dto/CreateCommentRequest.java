package com.jvmd.llmbrainservice.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record CreateCommentRequest(
        String id,
        String title,
        String body,
        String preview,
        String kind,
        String subjectKind,
        String subjectId,
        List<String> referencedLawCodes,
        List<CommentArticleRef> referencedArticles
) {}