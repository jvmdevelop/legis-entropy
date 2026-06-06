package com.jvmd.llmbrainservice.service.draft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvmd.llmbrainservice.client.DmsClient;
import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.dto.CreateTemplateResponse;
import com.jvmd.llmbrainservice.dto.DraftResult;
import com.jvmd.llmbrainservice.dto.DraftStepNotification;
import com.jvmd.llmbrainservice.dto.RenderTemplateRequest;
import com.jvmd.llmbrainservice.dto.UserTemplate;
import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.service.llm.BrainModelClient;
import com.jvmd.llmbrainservice.util.RateLimitRetry;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentDraftService {

    private final BrainModelClient modelClient;
    private final GraphServiceClient graphServiceClient;
    private final DmsClient dmsClient;
    private final ObjectMapper objectMapper; // Встроенный бин Spring Boot для работы с JSON

    public BrainResponse draft(BrainRequest request) {
        try {
            DraftResult result = executeDraft(request, null);
            return new BrainResponse(buildConfirmationText(result), 0);
        } catch (Exception e) {
            log.error("Document draft failed for user {}: {}", request.userId(), e.getMessage(), e);
            return new BrainResponse("Не удалось подготовить документ: " + e.getMessage(), 0);
        }
    }

    public SseEmitter streamDraft(BrainRequest request) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        CompletableFuture.runAsync(() -> {
            try {
                DraftResult result = executeDraft(request, emitter);

                emitter.send(SseEmitter.event()
                        .name("document-draft-result")
                        .data(result));

                emitter.send(SseEmitter.event()
                        .data(buildConfirmationText(result)));

                emitter.complete();
            } catch (Exception e) {
                log.error("Document draft stream failed for user {}: {}", request.userId(), e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Ошибка при подготовке документа: " + e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private DraftResult executeDraft(BrainRequest request, SseEmitter emitter) {
        emitThinking(emitter, new DraftStepNotification("draft-laws", "Ищу применимые нормы..."));
        List<String> lawCodes = findRelevantLaws(request.message());
        log.info("Draft: found {} law(s) for user {}", lawCodes.size(), request.userId());

        String situationId = null;
        if (request.hasGraphId()) {
            emitThinking(emitter, new DraftStepNotification("draft-situation", "Создаю ситуацию в графе..."));
            situationId = createSituationInGraph(request);

            if (situationId != null) {
                emitThinking(emitter, new DraftStepNotification("draft-link", "Привязываю законы к ситуации..."));
                linkLawsToSituation(request.graphId(), situationId, lawCodes);
            }
        }

        emitThinking(emitter, new DraftStepNotification("draft-generate", "Генерирую текст документа..."));
        String docContent = generateDocumentContent(request.message(), lawCodes);

        emitThinking(emitter, new DraftStepNotification("draft-template", "Создаю шаблон..."));

        String templateId = Optional.ofNullable(createTemplate(request.userId(), docContent, lawCodes, request.message()))
                .map(CreateTemplateResponse::id)
                .orElse(null);

        String generatedDocId = null;
        String docTitle = shortTitle(request.message());

        if (templateId != null) {
            emitThinking(emitter, new DraftStepNotification("draft-save", "Сохраняю документ..."));
            generatedDocId = Optional.ofNullable(dmsClient.renderTemplate(
                    templateId,
                    request.userId(),
                    new RenderTemplateRequest(docTitle, request.graphId(), situationId)
            )).map(res -> res.id()).orElse(null);
        }

        if (generatedDocId != null && situationId != null && request.hasGraphId()) {
            graphServiceClient.updateSituationGeneratedDoc(
                    request.graphId(),
                    situationId,
                    generatedDocId,
                    docTitle
            );
        }

        return new DraftResult(templateId, generatedDocId, situationId, lawCodes);
    }

    private List<String> findRelevantLaws(String message) {
        return graphServiceClient
                .searchLaws(message, "RK")
                .stream()
                .limit(5)
                .map(GraphServiceClient.LawInfo::getCode)
                .toList();
    }

    private String createSituationInGraph(BrainRequest request) {
        String title = shortTitle(request.message());
        String plain = request.message().length() > 500
                ? request.message().substring(0, 500)
                : request.message();
        return graphServiceClient
                .createSituation(request.graphId(), request.userId(), title, plain)
                .orElse(null);
    }

    private void linkLawsToSituation(String graphId, String situationId, List<String> lawCodes) {
        for (String code : lawCodes) {
            graphServiceClient.addLawToUserGraph(graphId, code, "RK");
            graphServiceClient.linkSituationToLaw(graphId, situationId, code, "RK", "SEMANTIC");
        }
    }

    private String generateDocumentContent(String userMessage, List<String> lawCodes) {
        String lawContext = lawCodes.isEmpty()
                ? ""
                : "Применимые нормы РК: " + String.join(", ", lawCodes) + ".\n\n";

        String prompt = """
                Ты — профессиональный юрист в Республике Казахстан.
                Составь юридический документ строго по запросу пользователя.
                Используй актуальное законодательство РК.
                Ответь ТОЛЬКО текстом самого документа в формате markdown — без вступлений, объяснений и комментариев.
                
                ВАЖНО: Все поля, которые пользователь должен заполнить (ФИО, дата, адрес, сумма, наименование суда и т.д.),
                обозначай токеном в формате {{название_поля}}, например:
                {{фио_истца}}, {{дата}}, {{адрес_ответчика}}, {{сумма_ущерба}}, {{наименование_суда}}.
                Не вставляй реальные имена и данные — только токены.
                
                %s
                Запрос пользователя:
                %s
                """.formatted(lawContext, userMessage);

        return RateLimitRetry.call(
                () -> modelClient.answer(prompt, List.of()).content(),
                15,
                "DocumentDraftService"
        );
    }

    private CreateTemplateResponse createTemplate(String userId, String docContent, List<String> lawCodes, String userMessage) {
        String code = "USR-DRAFT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String title = "Черновик: " + shortTitle(userMessage);
        String description = "Создан ИИ по запросу пользователя. Можно редактировать и повторно сгенерировать документ.";

        String suggestedLawCodesJson;
        try {
            suggestedLawCodesJson = objectMapper.writeValueAsString(lawCodes);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize law codes", e);
            suggestedLawCodesJson = "[]";
        }

        return dmsClient.createTemplate(
                userId,
                new UserTemplate(code, title, description, "USER", docContent, suggestedLawCodesJson, "MANUAL", false)
        );
    }

    private String shortTitle(String message) {
        if (message == null || message.isBlank()) return "Документ";
        String normalized = message.trim();
        for (int i = 0; i < Math.min(normalized.length(), 80); i++) {
            char c = normalized.charAt(i);
            if (c == '.' || c == '\n') {
                return normalized.substring(0, i).trim();
            }
        }
        return normalized.length() > 80
                ? normalized.substring(0, 77) + "..."
                : normalized;
    }

    private String buildConfirmationText(DraftResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Документ подготовлен.**\n\n");

        if (!r.lawCodes().isEmpty()) {
            sb.append("Применимые нормы: **")
                    .append(String.join(", ", r.lawCodes()))
                    .append("**\n\n");
        }
        if (r.situationId() != null) {
            sb.append("Ситуация создана в графе, законы привязаны.\n\n");
        }
        if (r.templateId() != null) {
            sb.append("Шаблон сохранён в разделе **Шаблоны** — вы можете его отредактировать ");
            sb.append("и повторно сгенерировать документ через кнопку «Применить».\n\n");
        }
        if (r.generatedDocId() != null) {
            sb.append("Готовый документ доступен в графе — откройте жёлтый узел рядом с ситуацией.");
        }
        return sb.toString();
    }

    private void emitThinking(SseEmitter emitter, DraftStepNotification notification) {
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("thinking").data(notification));
        } catch (IOException e) {
            log.debug("Could not send draft thinking event: {}", e.getMessage());
        }
    }
}