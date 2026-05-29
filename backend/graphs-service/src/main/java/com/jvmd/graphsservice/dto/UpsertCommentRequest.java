package com.jvmd.graphsservice.dto;

import java.util.List;
import lombok.Data;

@Data
public class UpsertCommentRequest {

    private String id;
    private String title;
    private String body;
    private String preview;

    private String kind;
    private String subjectKind;
    private String subjectId;

    private List<String> referencedLawCodes;

    private List<ArticleRef> referencedArticles;

    @Data
    public static class ArticleRef {

        private String lawCode;
        private String number;
        private String country;
        private String reason;
    }
}
