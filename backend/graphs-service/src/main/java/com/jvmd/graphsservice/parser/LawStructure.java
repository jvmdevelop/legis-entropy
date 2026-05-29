package com.jvmd.graphsservice.parser;

import com.jvmd.graphsservice.model.Article;
import java.time.LocalDate;
import java.util.List;

public final class LawStructure {

    private LawStructure() {}

    public record ExtractionResult(
        List<Article> articles,
        List<AmendmentEdge> amendments,
        List<CrossRefEdge> crossRefs
    ) {}

    public record AmendmentEdge(
        String articleNumber,
        String byDocId,
        String byInternalNumber,
        LocalDate amendmentDate,
        LocalDate effectiveFrom,
        String anchor,
        String contextSnippet
    ) {}

    public record CrossRefEdge(
        String fromArticleNumber,
        String toDocId,
        String toAnchor,
        String contextSnippet
    ) {}
}
