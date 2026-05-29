package com.jvmd.dms.voice.controller;

import com.jvmd.dms.voice.dto.VoiceMessageDTO;
import com.jvmd.dms.voice.service.VoiceMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/voice-messages")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VoiceMessageController {

    private final VoiceMessageService voiceService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VoiceMessageDTO> upload(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(required = false) String graphId,
            @RequestParam(required = false) String situationId,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false, defaultValue = "ru") String language,
            @RequestPart("file") MultipartFile file) {
        String uid = userId != null ? userId : "anonymous";
        VoiceMessageDTO dto = voiceService.upload(uid, graphId, situationId, conversationId, language, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public List<VoiceMessageDTO> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(required = false) String graphId,
            @RequestParam(required = false) String situationId) {
        if (situationId != null) return voiceService.listBySituation(situationId);
        if (graphId != null) return voiceService.listByGraph(graphId);
        String uid = userId != null ? userId : "anonymous";
        return voiceService.listByUser(uid);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VoiceMessageDTO> get(@PathVariable String id) {
        return voiceService.get(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reanalyze")
    public ResponseEntity<VoiceMessageDTO> reanalyze(@PathVariable String id) {
        return voiceService.transcribeAndAnalyze(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/audio")
    public ResponseEntity<byte[]> downloadAudio(@PathVariable String id) {
        var dto = voiceService.get(id).orElse(null);
        if (dto == null) return ResponseEntity.notFound().build();
        byte[] bytes = voiceService.download(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, dto.getContentType() != null ? dto.getContentType() : "audio/mpeg")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + (dto.getFileName() != null ? dto.getFileName() : "voice.mp3") + "\"")
                .body(bytes);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        voiceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
