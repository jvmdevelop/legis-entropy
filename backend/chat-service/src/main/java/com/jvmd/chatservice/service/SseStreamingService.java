package com.jvmd.chatservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvmd.chatservice.dto.BrainRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseStreamingService {

    private final RestTemplate loadBalancedRestTemplate;
    private final ObjectMapper objectMapper;

    public SseEmitter stream(String url, BrainRequestDto request, Long conversationId, Consumer<String> onComplete) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        CompletableFuture.runAsync(() -> {
            StringBuilder fullContent = new StringBuilder();
            try {
                loadBalancedRestTemplate.execute(url, HttpMethod.POST,
                    req -> {
                        req.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        req.getHeaders().setAccept(List.of(MediaType.TEXT_EVENT_STREAM));
                        objectMapper.writeValue(req.getBody(), request);
                    },
                    resp -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(resp.getBody(), StandardCharsets.UTF_8))) {
                            parseSseStream(reader, emitter, fullContent);
                        } catch (Exception ex) {
                            throw new IOException(ex);
                        }
                        return null;
                    }
                );
                onComplete.accept(fullContent.toString());
                emitter.complete();
            } catch (Exception e) {
                log.error("Stream error for conversation {}: {}", conversationId, e.getMessage());
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void parseSseStream(BufferedReader reader, SseEmitter emitter, StringBuilder fullContent) throws Exception {
        String line;
        StringBuilder eventBuf = new StringBuilder();
        String currentEventType = "";
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("event:")) {
                currentEventType = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (eventBuf.length() > 0) eventBuf.append('\n');
                eventBuf.append(line.substring(5));
            } else if (line.isEmpty() && eventBuf.length() > 0) {
                currentEventType = flushSseEvent(eventBuf, currentEventType, emitter, fullContent);
            }
        }
        if (eventBuf.length() > 0) {
            flushSseEvent(eventBuf, currentEventType, emitter, fullContent);
        }
    }

    private String flushSseEvent(StringBuilder eventBuf, String eventType, SseEmitter emitter, StringBuilder fullContent) throws Exception {
        String chunk = eventBuf.toString();
        eventBuf.setLength(0);
        if (!eventType.isEmpty()) {
            emitter.send(SseEmitter.event().name(eventType).data(chunk));
            if ("redaction".equals(eventType)) {
                fullContent.setLength(0);
                fullContent.append(chunk);
            }
        } else {
            fullContent.append(chunk);
            emitter.send(SseEmitter.event().data(chunk));
        }
        return "";
    }
}
