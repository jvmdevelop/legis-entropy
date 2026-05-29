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
public class LawResponseDto {

    private UUID id;
    private String title;
    private String code;
    private String documentType;
    private String documentNumber;
    private LocalDate datePublished;
    private String issuedBy;
    private Integer version;
    private String hierarchyLevel;
    private Integer articleCount;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getLabel() {
        if ("CONSTITUTION".equals(documentType)) {
            return "Конституция РК";
        } else if ("CODE".equals(documentType)) {
            return code != null ? code + " РК" : title;
        } else if ("LAW".equals(documentType)) {
            return "Закон РК: " + title;
        } else {
            return title;
        }
    }
}
