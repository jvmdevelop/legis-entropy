package com.jvmd.chatservice.dto;

import com.jvmd.chatservice.model.MessageRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    @NotNull(message = "Conversation ID is required")
    private Long conversationId;

    @NotNull(message = "Role is required")
    private MessageRole role;

    @NotBlank(message = "Content is required")
    private String content;

    private Integer tokens;
}
