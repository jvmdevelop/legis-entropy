package com.jvmd.chatservice.client;

import com.jvmd.chatservice.dto.BrainRequestDto;
import com.jvmd.chatservice.dto.BrainResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LlmBrainClientFallbackFactory implements FallbackFactory<LlmBrainClient> {

    @Override
    public LlmBrainClient create(Throwable cause) {
        return new LlmBrainClient() {
            @Override
            public BrainResponseDto chat(BrainRequestDto request) {
                log.error("LLM brain service unavailable: {}", cause.getMessage());
                return new BrainResponseDto(
                        "Сервис ИИ временно недоступен. Пожалуйста, попробуйте позже.", 0);
            }
        };
    }
}
