package com.jvmd.lawservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponseDto {

    private UUID id;
    private Integer articleNumber;
    private String title;
    private String fullText;
    private String notes;
    private String lawCode;
    private String lawTitle;
    private Integer version;
    private LocalDate changedAt;
    private Boolean isActive;
    private Integer clauseCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getFullReference() {
        return String.format("Статья %d %s", articleNumber, lawTitle);
    }
}
