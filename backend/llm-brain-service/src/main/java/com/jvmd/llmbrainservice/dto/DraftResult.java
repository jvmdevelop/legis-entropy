package com.jvmd.llmbrainservice.dto;

import java.util.List;

public record DraftResult(
            String templateId,
            String generatedDocId,
            String situationId,
            List<String> lawCodes
    ) {
    }