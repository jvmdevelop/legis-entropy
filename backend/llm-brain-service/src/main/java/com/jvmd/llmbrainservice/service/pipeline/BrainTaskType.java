package com.jvmd.llmbrainservice.service.pipeline;

public enum BrainTaskType {
    DOCUMENT_AUDIT,
    DOCUMENT_QA,
    LAW_SEARCH,
    SUMMARY,
    GENERAL_LEGAL_ADVICE,
    GRAPH_LINK_DOCUMENT,
    GRAPH_ADD_LAWS,
    GRAPH_DEEP_ANALYSIS,
    DOCUMENT_DRAFT;

    public boolean requiresGraphId() {
        return this == GRAPH_LINK_DOCUMENT || this == GRAPH_ADD_LAWS || this == GRAPH_DEEP_ANALYSIS;
    }

    public boolean isDocumentTask() {
        return this == DOCUMENT_AUDIT || this == DOCUMENT_QA || this == SUMMARY;
    }
}
