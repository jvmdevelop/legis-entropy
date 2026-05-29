package com.jvmd.graphsservice.dto;

import com.jvmd.graphsservice.model.ChangeEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeEventDTO {
    private String id;
    private String subscriptionId;
    private String lawCode;
    private String country;
    private String articleNumber;
    private LocalDate oldAmendmentDate;
    private LocalDate newAmendmentDate;
    private LocalDateTime detectedAt;
    private boolean acknowledged;

    public static ChangeEventDTO from(ChangeEvent e) {
        return ChangeEventDTO.builder()
                .id(e.getId())
                .subscriptionId(e.getSubscriptionId())
                .lawCode(e.getLawCode())
                .country(e.getCountry())
                .articleNumber(e.getArticleNumber())
                .oldAmendmentDate(e.getOldAmendmentDate())
                .newAmendmentDate(e.getNewAmendmentDate())
                .detectedAt(e.getDetectedAt())
                .acknowledged(e.isAcknowledged())
                .build();
    }
}
