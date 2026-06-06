package com.jvmd.llmbrainservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "voice-service")
public interface VoiceClient {

    @GetMapping("/api/voice-messages/{voiceId}")
    Map<String, Object> getVoiceMessage(@PathVariable("voiceId") String voiceId);
}
