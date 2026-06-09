package com.jvmd.documentservice.evidence;

import com.jvmd.documentservice.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/evidence-pack")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class EvidencePackController {

    private final VoiceMessageRepository voiceRepo;
    private final GeneratedDocumentRepository docRepo;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @GetMapping(value = "/by-graph/{graphId}", produces = "application/zip")
    public ResponseEntity<ByteArrayResource> packByGraph(@PathVariable String graphId) {
        return zip("evidence-graph-" + safe(graphId),
                voiceRepo.findByGraphIdOrderByCreatedAtDesc(graphId),
                docRepo.findByGraphIdOrderByCreatedAtDesc(graphId));
    }

    @GetMapping(value = "/by-situation/{situationId}", produces = "application/zip")
    public ResponseEntity<ByteArrayResource> packBySituation(@PathVariable String situationId) {
        return zip("evidence-situation-" + safe(situationId),
                voiceRepo.findBySituationIdOrderByCreatedAtDesc(situationId),
                docRepo.findBySituationIdOrderByCreatedAtDesc(situationId));
    }

    private ResponseEntity<ByteArrayResource> zip(String baseName, List<VoiceMessage> voices, List<GeneratedDocument> docs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeReadme(zip, baseName, voices, docs);
            for (VoiceMessage v : voices) writeVoice(zip, v);
            for (GeneratedDocument d : docs) writeGeneratedDocument(zip, d);
        } catch (Exception e) {
            log.error("Could not build evidence pack {}: {}", baseName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
        byte[] bytes = out.toByteArray();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private void writeReadme(ZipOutputStream zip, String baseName, List<VoiceMessage> voices, List<GeneratedDocument> docs) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# Доказательственный пакет\n\n");
        sb.append("Сборка: ").append(LocalDateTime.now()).append("\n");
        sb.append("Идентификатор: ").append(baseName).append("\n\n");
        sb.append("## Содержимое\n\n");
        sb.append("- `transcripts/` — расшифровки голосовых сообщений (Markdown)\n");
        sb.append("- `audio/` — исходные аудиофайлы\n");
        sb.append("- `documents/` — сгенерированные документы\n");
        sb.append("- `summary.md` — короткая сводка по делу\n\n");
        sb.append("## Голосовые сообщения\n\n");
        if (voices.isEmpty()) {
            sb.append("_Нет_\n");
        } else {
            for (VoiceMessage v : voices)
                sb.append("- `").append(safe(v.getId())).append("` — ").append(v.getStatus()).append(", создано ").append(v.getCreatedAt()).append("\n");
        }
        sb.append("\n## Документы\n\n");
        if (docs.isEmpty()) {
            sb.append("_Нет_\n");
        } else {
            for (GeneratedDocument d : docs)
                sb.append("- ").append(d.getTitle()).append(" (").append(d.getTemplateCode() != null ? d.getTemplateCode() : "CUSTOM").append(") — `").append(safe(d.getId())).append("`\n");
        }
        writeEntry(zip, "README.md", sb.toString().getBytes(StandardCharsets.UTF_8));
        String summary = "# Сводка\n\nВсего голосовых сообщений: " + voices.size() + "\nВсего документов: " + docs.size() +
                "\n\nДокумент собран автоматически. Проверьте каждый файл перед подачей в органы.\n";
        writeEntry(zip, "summary.md", summary.getBytes(StandardCharsets.UTF_8));
    }

    private void writeVoice(ZipOutputStream zip, VoiceMessage v) throws Exception {
        StringBuilder md = new StringBuilder();
        md.append("# Голосовое сообщение ").append(v.getId()).append("\n\n");
        md.append("- Создано: ").append(v.getCreatedAt()).append("\n");
        md.append("- Статус: ").append(v.getStatus()).append("\n");
        if (v.getLanguage() != null) md.append("- Язык: ").append(v.getLanguage()).append("\n");
        if (v.getDurationMs() != null) md.append("- Длительность: ").append(v.getDurationMs()).append(" мс\n");
        md.append("\n## Транскрипт\n\n").append(v.getTranscript() == null || v.getTranscript().isBlank() ? "_(нет)_" : v.getTranscript());
        if (v.getAnalysisJson() != null && !v.getAnalysisJson().isBlank())
            md.append("\n\n## Анализ (JSON)\n\n```json\n").append(v.getAnalysisJson()).append("\n```\n");
        if (v.getTranscriptJson() != null && !v.getTranscriptJson().isBlank())
            md.append("\n\n## Сегменты (JSON)\n\n```json\n").append(v.getTranscriptJson()).append("\n```\n");
        writeEntry(zip, "transcripts/" + safe(v.getId()) + ".md", md.toString().getBytes(StandardCharsets.UTF_8));

        if (v.getStatus() != VoiceMessageStatus.ERROR && v.getObjectName() != null) {
            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder().bucket(minioProperties.getBucket()).object(v.getObjectName()).build())) {
                String ext = extensionFor(v.getContentType(), v.getFileName());
                writeEntry(zip, "audio/" + safe(v.getId()) + "." + ext, in.readAllBytes());
            } catch (Exception e) {
                log.warn("Skipping audio for voice {}: {}", v.getId(), e.getMessage());
            }
        }
    }

    private void writeGeneratedDocument(ZipOutputStream zip, GeneratedDocument d) throws Exception {
        String name = "documents/" + safe(d.getTemplateCode() != null ? d.getTemplateCode() : "doc") + "_" + safe(d.getId()) + ".md";
        StringBuilder md = new StringBuilder();
        md.append("# ").append(d.getTitle()).append("\n\n");
        md.append("- Шаблон: ").append(d.getTemplateCode() != null ? d.getTemplateCode() : "CUSTOM").append("\n");
        md.append("- Создан: ").append(d.getCreatedAt()).append("\n\n---\n\n");
        md.append(d.getBodyMarkdown() == null ? "" : d.getBodyMarkdown());
        writeEntry(zip, name, md.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void writeEntry(ZipOutputStream zip, String name, byte[] data) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    private static String safe(String s) {
        if (s == null) return "unknown";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-zа-я0-9._-]+", "_");
    }

    private static String extensionFor(String contentType, String fileName) {
        if (fileName != null) {
            int dot = fileName.lastIndexOf('.');
            if (dot > 0 && dot < fileName.length() - 1) return fileName.substring(dot + 1);
        }
        if (contentType == null) return "bin";
        if (contentType.contains("webm")) return "webm";
        if (contentType.contains("mp4")) return "m4a";
        if (contentType.contains("mpeg")) return "mp3";
        if (contentType.contains("wav")) return "wav";
        if (contentType.contains("ogg")) return "ogg";
        return "bin";
    }
}
