package com.jvmd.llmbrainservice.client;

import com.jvmd.llmbrainservice.dto.CreateTemplateResponse;
import com.jvmd.llmbrainservice.dto.RenderTemplateRequest;
import com.jvmd.llmbrainservice.dto.RenderTemplateResponse;
import com.jvmd.llmbrainservice.dto.UserTemplate;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "template-service")
public interface TemplateClient {

    @PostMapping(value = "/api/templates", consumes = MediaType.APPLICATION_JSON_VALUE)
    CreateTemplateResponse createTemplate(@RequestHeader("X-User-Id") String userId, @RequestBody UserTemplate userTemplate);

    @PostMapping(value = "/api/templates/{templateId}/render", consumes = MediaType.APPLICATION_JSON_VALUE)
    RenderTemplateResponse renderTemplate(@PathVariable("templateId") String templateId, @RequestHeader("X-User-Id") String userId, @RequestBody RenderTemplateRequest renderRequest);
}
