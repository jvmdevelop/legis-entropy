package com.jvmd.dms.voice.service;

import com.jvmd.dms.config.MinioProperties;
import com.jvmd.dms.voice.dto.VoiceMessageDTO;
import com.jvmd.dms.voice.model.VoiceMessage;
import com.jvmd.dms.voice.model.VoiceMessageStatus;
import com.jvmd.dms.voice.repository.VoiceMessageRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceMessageService {

    private final VoiceMessageRepository repository;
    private final VoiceTranscriptionService transcriptionService;
    private final VoiceAnalysisService analysisService;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Transactional
    public VoiceMessageDTO upload(
        String userId,
        String graphId,
        String situationId,
        String conversationId,
        String languageHint,
        MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Audio file is empty");
        }
        String id = "voice-" + UUID.randomUUID();
        String objectName = storeAudio(id, userId, file);

        VoiceMessage v = new VoiceMessage();
        v.setId(id);
        v.setUserId(userId);
        v.setGraphId(graphId);
        v.setSituationId(situationId);
        v.setConversationId(conversationId);
        v.setFileName(file.getOriginalFilename());
        v.setContentType(file.getContentType());
        v.setObjectName(objectName);
        v.setLanguage(languageHint);
        v.setStatus(VoiceMessageStatus.UPLOADED);

        VoiceMessage saved = repository.save(v);
        log.info(
            "Uploaded voice message {} ({} bytes) for user {}",
            id,
            file.getSize(),
            userId
        );

        processAsync(id);
        return VoiceMessageDTO.from(saved);
    }

    @Async
    public void processAsync(String voiceMessageId) {
        try {
            transcribeAndAnalyze(voiceMessageId);
        } catch (Exception e) {
            log.error(
                "Voice processing failed for {}: {}",
                voiceMessageId,
                e.getMessage(),
                e
            );
            repository.findById(voiceMessageId).ifPresent(v -> {
                v.setStatus(VoiceMessageStatus.ERROR);
                v.setErrorMessage(e.getMessage());
                repository.save(v);
            });
        }
    }

    @Transactional
    public Optional<VoiceMessageDTO> transcribeAndAnalyze(String id) {
        Optional<VoiceMessage> opt = repository.findById(id);
        if (opt.isEmpty()) return Optional.empty();
        VoiceMessage v = opt.get();

        v.setStatus(VoiceMessageStatus.TRANSCRIBING);
        repository.save(v);

        try (InputStream in = loadAudio(v.getObjectName())) {
            VoiceTranscriptionService.TranscriptionResult tr =
                transcriptionService.transcribe(
                    in,
                    v.getContentType(),
                    v.getLanguage()
                );
            v.setTranscript(tr.plainText());
            v.setTranscriptJson(serializeSegments(tr.segments()));
            v.setLanguage(tr.language());
            v.setDurationMs(tr.durationMs());
            v.setStatus(VoiceMessageStatus.TRANSCRIBED);
            repository.save(v);

            v.setStatus(VoiceMessageStatus.ANALYZING);
            repository.save(v);

            List<String> speakers = tr
                .segments()
                .stream()
                .map(VoiceTranscriptionService.Segment::speaker)
                .filter(s -> s != null)
                .distinct()
                .toList();
            VoiceAnalysisService.Classification cls = analysisService.classify(
                tr.plainText(),
                speakers
            );
            v.setAnalysisJson(serializeClassification(cls));
            v.setStatus(VoiceMessageStatus.ANALYZED);
            repository.save(v);
        } catch (Exception e) {
            v.setStatus(VoiceMessageStatus.ERROR);
            v.setErrorMessage(e.getMessage());
            repository.save(v);
            log.warn("transcribeAndAnalyze({}) failed: {}", id, e.getMessage());
        }
        return Optional.of(VoiceMessageDTO.from(v));
    }

    public Optional<VoiceMessageDTO> get(String id) {
        return repository.findById(id).map(VoiceMessageDTO::from);
    }

    public List<VoiceMessageDTO> listByUser(String userId) {
        return repository
            .findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(VoiceMessageDTO::from)
            .toList();
    }

    public List<VoiceMessageDTO> listByGraph(String graphId) {
        return repository
            .findByGraphIdOrderByCreatedAtDesc(graphId)
            .stream()
            .map(VoiceMessageDTO::from)
            .toList();
    }

    public List<VoiceMessageDTO> listBySituation(String situationId) {
        return repository
            .findBySituationIdOrderByCreatedAtDesc(situationId)
            .stream()
            .map(VoiceMessageDTO::from)
            .toList();
    }

    @Transactional
    public void delete(String id) {
        repository.findById(id).ifPresent(v -> {
            try {
                io.minio.RemoveObjectArgs args =
                    io.minio.RemoveObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(v.getObjectName())
                        .build();
                minioClient.removeObject(args);
            } catch (Exception e) {
                log.warn(
                    "Could not remove voice object {}: {}",
                    v.getObjectName(),
                    e.getMessage()
                );
            }
            repository.delete(v);
        });
    }

    public byte[] download(String id) {
        VoiceMessage v = repository
            .findById(id)
            .orElseThrow(() ->
                new IllegalArgumentException("Voice message not found: " + id)
            );
        try (InputStream in = loadAudio(v.getObjectName())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Could not load voice audio", e);
        }
    }

    private String storeAudio(String id, String userId, MultipartFile file) {
        try {
            ensureBucket();
            String safeName =
                file.getOriginalFilename() == null
                    ? "audio.bin"
                    : file
                          .getOriginalFilename()
                          .toLowerCase(Locale.ROOT)
                          .replaceAll("[^a-zа-я0-9._-]+", "_");
            String objectName = userId + "/voice/" + id + "/" + safeName;
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(objectName)
                        .contentType(file.getContentType())
                        .stream(in, file.getSize(), -1)
                        .build()
                );
            }
            return objectName;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Could not store voice audio in MinIO",
                e
            );
        }
    }

    private InputStream loadAudio(String objectName) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectName)
                    .build()
            );
        } catch (Exception e) {
            log.warn(
                "Could not load audio {} from MinIO: {}",
                objectName,
                e.getMessage()
            );
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
            BucketExistsArgs.builder()
                .bucket(minioProperties.getBucket())
                .build()
        );
        if (!exists) {
            minioClient.makeBucket(
                MakeBucketArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .build()
            );
        }
    }

    private String serializeSegments(
        List<VoiceTranscriptionService.Segment> segments
    ) {
        if (segments == null || segments.isEmpty()) return "[]";
        return segments
            .stream()
            .map(
                s ->
                    "{\"speaker\":\"" +
                    jsonEscape(s.speaker()) +
                    "\",\"startMs\":" +
                    s.startMs() +
                    ",\"endMs\":" +
                    s.endMs() +
                    ",\"text\":\"" +
                    jsonEscape(s.text()) +
                    "\"}"
            )
            .collect(Collectors.joining(",", "[", "]"));
    }

    private String serializeClassification(
        VoiceAnalysisService.Classification c
    ) {
        if (c == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{\"kind\":\"").append(jsonEscape(c.kind())).append("\",");
        sb.append("\"severity\":\"")
            .append(jsonEscape(c.severity()))
            .append("\",");
        sb.append("\"summary\":\"")
            .append(jsonEscape(c.summary()))
            .append("\",");
        sb.append("\"quotes\":[");
        sb.append(
            c
                .quotes()
                .stream()
                .map(q -> "\"" + jsonEscape(q) + "\"")
                .collect(Collectors.joining(","))
        );
        sb.append("],");
        sb.append("\"articles\":[");
        List<String> arts = new ArrayList<>();
        for (VoiceAnalysisService.SuggestedArticle a : c.articles()) {
            arts.add(
                "{\"lawCode\":\"" +
                    jsonEscape(a.lawCode()) +
                    "\",\"number\":\"" +
                    jsonEscape(a.number()) +
                    "\",\"reason\":\"" +
                    jsonEscape(a.reason()) +
                    "\"}"
            );
        }
        sb.append(String.join(",", arts));
        sb.append("]}");
        return sb.toString();
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
