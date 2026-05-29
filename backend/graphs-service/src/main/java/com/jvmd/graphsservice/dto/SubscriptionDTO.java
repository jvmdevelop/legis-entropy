package com.jvmd.graphsservice.dto;

import com.jvmd.graphsservice.model.Subscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionDTO {
    private String id;
    private String userId;
    private String lawCode;
    private String country;
    private String articleNumber;
    private String articleTitle;
    private LocalDateTime createdAt;
    private LocalDateTime lastCheckedAt;
    private int unreadEvents;

    public static SubscriptionDTO from(Subscription s, String articleTitle, int unreadEvents) {
        return SubscriptionDTO.builder()
                .id(s.getId())
                .userId(s.getUserId())
                .lawCode(s.getLawCode())
                .country(s.getCountry())
                .articleNumber(s.getArticleNumber())
                .articleTitle(articleTitle)
                .createdAt(s.getCreatedAt())
                .lastCheckedAt(s.getLastCheckedAt())
                .unreadEvents(unreadEvents)
                .build();
    }
}
