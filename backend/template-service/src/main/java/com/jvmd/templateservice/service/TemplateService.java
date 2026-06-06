package com.jvmd.templateservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvmd.templateservice.config.MinioProperties;
import com.jvmd.templateservice.dto.GeneratedDocumentDTO;
import com.jvmd.templateservice.dto.RenderTemplateRequest;
import com.jvmd.templateservice.dto.TemplateDTO;
import com.jvmd.templateservice.entity.DocumentTemplate;
import com.jvmd.templateservice.entity.GeneratedDocument;
import com.jvmd.templateservice.model.Slot;
import com.jvmd.templateservice.repository.DocumentTemplateRepository;
import com.jvmd.templateservice.repository.GeneratedDocumentRepository;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final DocumentTemplateRepository templateRepository;
    private final GeneratedDocumentRepository generatedRepository;
    private final TemplateRenderer renderer;
    private final TemplateConversionService conversionService;
    private final SlotDetector slotDetector;
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final ObjectMapper objectMapper;

    public List<TemplateDTO> listVisibleToUser(String userId) {
        String uid = userId == null || userId.isBlank() ? "__none__" : userId;
        return templateRepository.findVisibleToUser(uid).stream().map(TemplateDTO::from).toList();
    }

    public List<TemplateDTO> listAll() {
        return templateRepository.findAll().stream().map(TemplateDTO::from).toList();
    }

    public List<TemplateDTO> listByKind(String kind) {
        return templateRepository.findByKind(kind).stream().map(TemplateDTO::from).toList();
    }

    public Optional<TemplateDTO> findById(String id) {
        return templateRepository.findById(id).map(TemplateDTO::from);
    }

    public Optional<TemplateDTO> findByCode(String code) {
        return templateRepository.findByCode(code).map(TemplateDTO::from);
    }

    @Transactional
    public TemplateDTO upsert(TemplateDTO dto, String userId) {
        DocumentTemplate t = templateRepository.findById(dto.getId() != null ? dto.getId() : "")
                .orElseGet(DocumentTemplate::new);
        if (t.getId() == null) {
            t.setId(dto.getId() != null ? dto.getId() : "tpl-" + UUID.randomUUID());
            t.setSystemTemplate(false);
            t.setUserId(userId);
        }
        t.setCode(dto.getCode());
        t.setTitle(dto.getTitle());
        t.setDescription(dto.getDescription());
        t.setKind(dto.getKind() != null ? dto.getKind() : "OTHER");
        t.setBody(dto.getBody());
        if (dto.getSlotsJson() != null && !dto.getSlotsJson().isBlank() && !dto.getSlotsJson().equals("[]")) {
            t.setSlotsJson(dto.getSlotsJson());
            t.setPlaceholdersJson(dto.getPlaceholdersJson() != null ? dto.getPlaceholdersJson() : dto.getSlotsJson());
        } else if (dto.getBody() != null && !dto.getBody().isBlank()) {
            SlotDetector.DetectionResult detection = slotDetector.detect(dto.getBody());
            t.setBody(detection.canonicalMarkdown());
            t.setSlotsJson(serializeSlots(detection.slots()));
            t.setPlaceholdersJson(serializeLegacy(detection.slots()));
        } else {
            t.setPlaceholdersJson(dto.getPlaceholdersJson());
            t.setSlotsJson(dto.getSlotsJson());
        }
        t.setSuggestedLawCodes(dto.getSuggestedLawCodes());
        if (dto.getSourceFormat() != null) t.setSourceFormat(dto.getSourceFormat());
        if (dto.getAiSummary() != null) t.setAiSummary(dto.getAiSummary());
        return TemplateDTO.from(templateRepository.save(t));
    }

    @Transactional
    public TemplateDTO uploadFromFile(MultipartFile file, String userId, String title, String kind) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Файл шаблона пустой");
        TemplateConversionService.ConversionResult conv = conversionService.convert(file);
        SlotDetector.DetectionResult detection = slotDetector.detect(conv.canonicalMarkdown());

        String id = "tpl-" + UUID.randomUUID();
        String fileName = file.getOriginalFilename() == null ? "template" : file.getOriginalFilename();
        String objectName = storeSource(id, userId, file);

        DocumentTemplate t = new DocumentTemplate();
        t.setId(id);
        t.setCode("USR-" + id.substring(4, 12).toUpperCase(Locale.ROOT));
        t.setTitle(title != null && !title.isBlank() ? title : stripExtension(fileName));
        t.setDescription("Загружен из " + conv.sourceFormat() + " (" + fileName + ")");
        t.setKind(kind != null && !kind.isBlank() ? kind : "USER");
        t.setBody(detection.canonicalMarkdown());
        t.setSlotsJson(serializeSlots(detection.slots()));
        t.setPlaceholdersJson(serializeLegacy(detection.slots()));
        t.setSystemTemplate(false);
        t.setUserId(userId);
        t.setSourceFormat(conv.sourceFormat());
        t.setSourceObjectName(objectName);

        DocumentTemplate saved = templateRepository.save(t);
        log.info("Imported template {} ({}) for user {} — {} slots", saved.getId(), conv.sourceFormat(), userId, detection.slots().size());
        return TemplateDTO.from(saved);
    }

    @Transactional
    public boolean deleteIfOwned(String templateId, String userId) {
        return templateRepository.findById(templateId)
                .filter(t -> !t.isSystemTemplate())
                .filter(t -> t.getUserId() != null && t.getUserId().equals(userId))
                .map(t -> {
                    if (t.getSourceObjectName() != null) {
                        try {
                            minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                                    .bucket(minioProperties.getBucket()).object(t.getSourceObjectName()).build());
                        } catch (Exception e) {
                            log.warn("Could not remove template source {}: {}", t.getSourceObjectName(), e.getMessage());
                        }
                    }
                    templateRepository.delete(t);
                    return true;
                }).orElse(false);
    }

    @Transactional
    public Optional<GeneratedDocumentDTO> render(String templateId, String userId, RenderTemplateRequest req) {
        return templateRepository.findById(templateId).map(template -> doRender(template, userId, req));
    }

    @Transactional
    public Optional<GeneratedDocumentDTO> renderByCode(String code, String userId, RenderTemplateRequest req) {
        return templateRepository.findByCode(code).map(template -> doRender(template, userId, req));
    }

    private GeneratedDocumentDTO doRender(DocumentTemplate template, String userId, RenderTemplateRequest req) {
        Map<String, String> vars = req.getVariables() == null ? Map.of()
                : req.getVariables().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue() == null ? "" : e.getValue().toString()));
        TemplateRenderer.RenderResult result = renderer.render(template.getBody(), vars);
        if (!result.unresolvedPlaceholders().isEmpty()) {
            log.info("Rendered template {} with unresolved placeholders: {}", template.getCode(), result.unresolvedPlaceholders());
        }
        GeneratedDocument gen = new GeneratedDocument();
        gen.setId("gen-" + UUID.randomUUID());
        gen.setUserId(userId);
        gen.setTemplateId(template.getId());
        gen.setTemplateCode(template.getCode());
        gen.setGraphId(req.getGraphId());
        gen.setSituationId(req.getSituationId());
        gen.setTitle(req.getTitle() != null && !req.getTitle().isBlank() ? req.getTitle() : template.getTitle());
        gen.setBodyMarkdown(result.body());
        gen.setVariablesJson(serializeVars(vars));
        GeneratedDocument saved = generatedRepository.save(gen);
        log.info("Rendered template {} → generated document {} for user {}", template.getCode(), saved.getId(), userId);
        return GeneratedDocumentDTO.from(saved);
    }

    @Transactional
    public Optional<GeneratedDocumentDTO> regenerate(String generatedDocId, String userId) {
        return generatedRepository.findById(generatedDocId).flatMap(orig -> {
            if (orig.getTemplateId() == null) return Optional.empty();
            return templateRepository.findById(orig.getTemplateId()).map(template -> {
                RenderTemplateRequest req = new RenderTemplateRequest();
                req.setTitle(orig.getTitle());
                req.setGraphId(orig.getGraphId());
                req.setSituationId(orig.getSituationId());
                req.setVariables(Map.of());
                return doRender(template, userId, req);
            });
        });
    }

    public List<GeneratedDocumentDTO> listGeneratedByUser(String userId) {
        return generatedRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(GeneratedDocumentDTO::from).toList();
    }

    public List<GeneratedDocumentDTO> listGeneratedBySituation(String situationId) {
        return generatedRepository.findBySituationIdOrderByCreatedAtDesc(situationId).stream().map(GeneratedDocumentDTO::from).toList();
    }

    public List<GeneratedDocumentDTO> listGeneratedByGraph(String graphId) {
        return generatedRepository.findByGraphIdOrderByCreatedAtDesc(graphId).stream().map(GeneratedDocumentDTO::from).toList();
    }

    public Optional<GeneratedDocumentDTO> getGenerated(String id) {
        return generatedRepository.findById(id).map(GeneratedDocumentDTO::from);
    }

    @Transactional
    public Optional<GeneratedDocumentDTO> updateBody(String id, String newMarkdown) {
        return generatedRepository.findById(id).map(doc -> {
            doc.setBodyMarkdown(newMarkdown);
            return GeneratedDocumentDTO.from(generatedRepository.save(doc));
        });
    }

    @Transactional
    public void deleteGenerated(String id) {
        generatedRepository.deleteById(id);
    }

    private String storeSource(String templateId, String userId, MultipartFile file) {
        try {
            ensureBucket();
            String safeName = file.getOriginalFilename() == null ? "template.bin"
                    : file.getOriginalFilename().toLowerCase(Locale.ROOT).replaceAll("[^a-zа-я0-9._-]+", "_");
            String objectName = (userId == null || userId.isBlank() ? "anonymous" : userId) + "/templates/" + templateId + "/" + safeName;
            try (InputStream in = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder().bucket(minioProperties.getBucket())
                        .object(objectName).contentType(file.getContentType()).stream(in, file.getSize(), -1).build());
            }
            return objectName;
        } catch (Exception e) {
            log.warn("Could not store template source for {}: {}", templateId, e.getMessage());
            return null;
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucket()).build());
        if (!exists) minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucket()).build());
    }

    private String serializeSlots(List<Slot> slots) {
        if (slots == null || slots.isEmpty()) return "[]";
        try { return objectMapper.writeValueAsString(slots); } catch (Exception e) { return "[]"; }
    }

    private String serializeLegacy(List<Slot> slots) {
        if (slots == null || slots.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"name\":\"").append(escape(s.getId())).append("\",\"label\":\"").append(escape(s.getLabel()))
              .append("\",\"required\":").append(s.isRequired()).append(s.isMultiline() ? ",\"multiline\":true" : "").append('}');
        }
        return sb.append(']').toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private String serializeVars(Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) return "{}";
        return vars.entrySet().stream()
                .map(e -> "\"" + jsonEscape(e.getKey()) + "\":\"" + jsonEscape(e.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
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
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        return sb.toString();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
