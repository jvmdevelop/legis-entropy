package com.jvmd.llmbrainservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class DmsTemplateClient {

    private final RestTemplate restTemplate;
    private final String dmsUrl;

    public DmsTemplateClient(RestTemplate restTemplate,
                             @Value("${dms.service.url:http://documents-manager-service}") String dmsUrl) {
        this.restTemplate = restTemplate;
        this.dmsUrl = dmsUrl;
    }

    @SuppressWarnings("unchecked")
    public Optional<String> createTemplate(String userId,
                                           String code,
                                           String title,
                                           String kind,
                                           String body,
                                           String description,
                                           String suggestedLawCodesJson) {
        try {
            String url = dmsUrl + "/api/templates";

            Map<String, Object> dto = new HashMap<>();
            dto.put("code", code);
            dto.put("title", title);
            dto.put("description", description);
            dto.put("kind", kind != null ? kind : "USER");
            dto.put("body", body);
            dto.put("suggestedLawCodes", suggestedLawCodesJson);
            dto.put("sourceFormat", "MANUAL");
            dto.put("systemTemplate", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", userId);

            Map<String, Object> response = restTemplate.postForObject(
                    url, new HttpEntity<>(dto, headers), Map.class);

            if (response == null) return Optional.empty();
            Object id = response.get("id");
            if (id == null) return Optional.empty();
            log.info("Created AI draft template {} ('{}') for user {}", id, title, userId);
            return Optional.of(id.toString());
        } catch (RestClientException e) {
            log.warn("Failed to create AI draft template: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<String> renderTemplate(String templateId,
                                           String userId,
                                           String title,
                                           String graphId,
                                           String situationId) {
        try {
            String url = dmsUrl + "/api/templates/" + templateId + "/render";

            Map<String, Object> req = new HashMap<>();
            req.put("title", title);
            req.put("variables", Map.of());
            if (graphId != null && !graphId.isBlank())       req.put("graphId", graphId);
            if (situationId != null && !situationId.isBlank()) req.put("situationId", situationId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", userId);

            Map<String, Object> response = restTemplate.postForObject(
                    url, new HttpEntity<>(req, headers), Map.class);

            if (response == null) return Optional.empty();
            Object id = response.get("id");
            if (id == null) return Optional.empty();
            log.info("Rendered template {} → generated doc {} for user {}", templateId, id, userId);
            return Optional.of(id.toString());
        } catch (RestClientException e) {
            log.warn("Failed to render template {}: {}", templateId, e.getMessage());
            return Optional.empty();
        }
    }
}
