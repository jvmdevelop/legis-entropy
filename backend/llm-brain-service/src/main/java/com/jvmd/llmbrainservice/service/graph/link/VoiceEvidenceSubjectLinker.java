package com.jvmd.llmbrainservice.service.graph.link;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.client.VoiceClient;
import com.jvmd.llmbrainservice.model.BrainRequest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoiceEvidenceSubjectLinker implements SubjectLinker {

    private final VoiceClient voiceClient;
    private final GraphServiceClient graphServiceClient;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public SubjectKind kind() {
        return SubjectKind.VOICE_EVIDENCE;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public boolean canHandle(BrainRequest request) {
        return request.hasVoiceId();
    }

    @Override
    public Optional<LinkableSubject> resolve(BrainRequest request) {
        if (!canHandle(request)) return Optional.empty();
        String label = fetchVoiceMap(request.voiceId())
                .map(m -> {
                    Object fn = m.get("fileName");
                    return fn == null ? null : fn.toString();
                })
                .orElse(null);
        return Optional.of(
                LinkableSubject.voice(
                        request.voiceId(),
                        request.userId() == null ? "" : request.userId(),
                        label
                )
        );
    }

    @Override
    public String fetchSearchableText(LinkableSubject subject, String graphId) {
        return fetchVoiceMap(subject.id())
                .map(m -> {
                    Object transcript = m.get("transcript");
                    if (transcript != null && !transcript.toString().isBlank()) {
                        return transcript.toString();
                    }
                    Object analysisJson = m.get("analysisJson");
                    return analysisJson == null ? "" : analysisJson.toString();
                })
                .orElse("");
    }

    @Override
    public boolean linkToLaw(
            String graphId,
            LinkableSubject subject,
            String lawCode,
            String country
    ) {
        try {
            graphServiceClient.linkVoiceEvidenceToLaw(
                    graphId,
                    subject.id(),
                    lawCode,
                    country,
                    "Suggested by AI from voice analysis"
            );
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> suggestedLawCodes(
            LinkableSubject subject,
            String graphId
    ) {
        return fetchVoiceMap(subject.id())
                .map(m -> m.get("analysisJson"))
                .map(Object::toString)
                .map(this::extractLawCodes)
                .orElseGet(List::of);
    }

    private List<String> extractLawCodes(String analysisJson) {
        if (analysisJson == null || analysisJson.isBlank()) return List.of();
        try {
            JsonNode root = MAPPER.readTree(analysisJson);
            JsonNode articles = root.get("articles");
            if (articles == null || !articles.isArray()) return List.of();
            LinkedHashSet<String> dedup = new LinkedHashSet<>();
            List<String> codes = new ArrayList<>();
            for (JsonNode a : articles) {
                JsonNode code = a.get("lawCode");
                if (code == null || code.isNull()) continue;
                String s = code.asText().trim();
                if (s.isEmpty()) continue;
                String key = s.toUpperCase(java.util.Locale.ROOT);
                if (dedup.add(key)) codes.add(s);
            }
            return codes;
        } catch (Exception e) {
            log.debug(
                    "Failed to parse analysisJson for voice seed codes: {}",
                    e.getMessage()
            );
            return List.of();
        }
    }

    @Override
    public String displayName() {
        return "Голосовое сообщение";
    }

    @Override
    public String displayNameAccusative() {
        return "голосовое сообщение";
    }

    @Override
    public String displayNameGenitive() {
        return "голосового сообщения";
    }

    private Optional<Map<String, Object>> fetchVoiceMap(String voiceId) {
        try {
            Map<String, Object> map = voiceClient.getVoiceMessage(voiceId);
            return Optional.ofNullable(map);
        } catch (Exception e) {
            log.debug(
                    "getVoiceMessage failed for {}: {}",
                    voiceId,
                    e.getMessage()
            );
            return Optional.empty();
        }
    }
}
