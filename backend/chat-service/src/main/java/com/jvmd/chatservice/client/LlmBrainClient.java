package com.jvmd.chatservice.client;

import com.jvmd.chatservice.dto.BrainRequestDto;
import com.jvmd.chatservice.dto.BrainResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "llm-brain-service", path = "/api/brain", fallbackFactory = LlmBrainClientFallbackFactory.class)
public interface LlmBrainClient {

    @PostMapping("/chat")
    BrainResponseDto chat(@RequestBody BrainRequestDto request);
}
