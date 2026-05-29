package com.jvmd.chatservice.dto;

import java.util.List;

public record BrainRequestDto(String message, String userId, String documentId,
                              String graphId, String situationId,
                              List<HistoryEntryDto> history) {
}
