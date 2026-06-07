package com.jvmd.chatservice.dto;

import com.jvmd.chatservice.model.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private Long id;
    private Long conversationId;
    private MessageRole role;
    private String content;
    private Integer tokens;
    private LocalDateTime createdAt;
}
