package com.jvmd.voiceservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "voice.stt.provider", havingValue = "whisper")
@Slf4j
public class WhisperServerVoiceTranscriptionService implements VoiceTranscriptionService {

    private final RestClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public WhisperServerVoiceTranscriptionService(
            @Value("${voice.stt.whisper.api-key:}") String apiKey,
            @Value("${voice.stt.whisper.base-url:http://whisper:8000/v1}") String baseUrl,
            @Value("${voice.stt.whisper.model:Systran/faster-whisper-base}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        log.info("Voice STT: using OpenAI-compatible endpoint {} with model {}", baseUrl, model);
    }

    @Override
    public TranscriptionResult transcribe(InputStream audio, String contentType, String languageHint) {
        byte[] bytes;
        try {
            bytes = audio.readAllBytes();
        } catch (Exception e) {
            log.error("Could not read audio bytes for transcription: {}", e.getMessage());
            return emptyResult(languageHint);
        }
        if (bytes.length == 0) return emptyResult(languageHint);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "voice." + extensionFor(contentType);
            }
        };
        body.add("file", fileResource);
        body.add("model", model);
        body.add("response_format", "verbose_json");
        body.add("temperature", "0");
        if (languageHint != null && !languageHint.isBlank()) {
            body.add("language", languageHint);
        }

        try {
            RestClient.RequestBodySpec spec = http.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA);
            if (apiKey != null && !apiKey.isBlank()) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            String response = spec.body(body).retrieve().body(String.class);
            return parseResponse(response, languageHint);
        } catch (Exception e) {
            log.error("Whisper STT call failed: {}", e.getMessage(), e);
            return emptyResult(languageHint);
        }
    }

    private TranscriptionResult parseResponse(String response, String fallbackLanguage) {
        if (response == null || response.isBlank()) return emptyResult(fallbackLanguage);
        try {
            JsonNode root = json.readTree(response);
            String text = root.path("text").asText("");
            String lang = root.path("language").asText(fallbackLanguage != null ? fallbackLanguage : "ru");
            double duration = root.path("duration").asDouble(0);
            List<Segment> segments = new ArrayList<>();
            JsonNode segArr = root.path("segments");
            if (segArr.isArray() && segArr.size() > 0) {
                for (JsonNode seg : segArr) {
                    int start = (int) Math.round(seg.path("start").asDouble(0) * 1000);
                    int end = (int) Math.round(seg.path("end").asDouble(0) * 1000);
                    String segText = seg.path("text").asText("").trim();
                    if (!segText.isEmpty()) {
                        segments.add(new Segment("SPEAKER_A", start, end, segText));
                    }
                }
            } else if (!text.isBlank()) {
                segments.add(new Segment("SPEAKER_A", 0, (int) Math.round(duration * 1000), text));
            }
            return new TranscriptionResult(lang, (int) Math.round(duration * 1000), text, segments);
        } catch (Exception e) {
            log.warn("Could not parse Whisper STT response: {}", e.getMessage());
            return emptyResult(fallbackLanguage);
        }
    }

    private TranscriptionResult emptyResult(String languageHint) {
        return new TranscriptionResult(languageHint != null ? languageHint : "ru", 0, "", List.of());
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) return "webm";
        if (contentType.contains("webm")) return "webm";
        if (contentType.contains("mp4")) return "m4a";
        if (contentType.contains("mpeg")) return "mp3";
        if (contentType.contains("wav")) return "wav";
        if (contentType.contains("ogg")) return "ogg";
        return "webm";
    }
}
