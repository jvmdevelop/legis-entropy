package com.jvmd.chatservice.mapper;

import com.jvmd.chatservice.dto.ConversationResponse;
import com.jvmd.chatservice.dto.MessageResponse;
import com.jvmd.chatservice.model.Conversation;
import com.jvmd.chatservice.model.Message;
import org.springframework.stereotype.Component;

@Component
public class ChatMapper {

    public MessageResponse toMessageResponse(Message message) {
        return new MessageResponse(
            message.getId(),
            message.getConversation().getId(),
            message.getRole(),
            message.getContent(),
            message.getTokens(),
            message.getCreatedAt()
        );
    }

    public ConversationResponse toConversationResponse(Conversation conversation) {
        return new ConversationResponse(
            conversation.getId(),
            conversation.getTitle(),
            conversation.getUserId(),
            conversation.getActive(),
            conversation.getCreatedAt(),
            conversation.getUpdatedAt()
        );
    }
}
