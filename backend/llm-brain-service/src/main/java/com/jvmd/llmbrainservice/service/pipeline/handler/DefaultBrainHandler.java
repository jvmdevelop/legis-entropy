package com.jvmd.llmbrainservice.service.pipeline.handler;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import com.jvmd.llmbrainservice.service.llm.BrainModelClient;
import com.jvmd.llmbrainservice.service.pipeline.AnswerPostProcessor;
import com.jvmd.llmbrainservice.service.pipeline.BrainTaskType;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.HallucinationDetector;
import com.jvmd.llmbrainservice.service.pipeline.ProfessionClassifier;
import com.jvmd.llmbrainservice.service.pipeline.retrieval.RetrievalStrategyRouter;
import com.jvmd.llmbrainservice.service.prompt.BrainPromptBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultBrainHandler implements BrainTaskHandler {

    private final RetrievalStrategyRouter retrievalStrategyRouter;
    private final BrainPromptBuilder promptBuilder;
    private final BrainModelClient modelClient;
    private final AnswerPostProcessor answerPostProcessor;
    private final ProfessionClassifier professionClassifier;
    private final HallucinationDetector hallucinationDetector;

    @Override
    public BrainTaskType taskType() {
        return BrainTaskType.GENERAL_LEGAL_ADVICE;
    }

    @Override
    public boolean isDefaultHandler() { return true; }

    @Override
    public BrainResponse handle(BrainRequest request, ContextPlan plan) {
        var profession = professionClassifier.classify(request.message());
        DocumentContext context = retrievalStrategyRouter.retrieve(request, plan);

        if (request.hasDocumentId() && context.unavailable()) {
            return new BrainResponse(documentUnavailableMessage(context.unavailableReason()), 0);
        }

        String prompt = promptBuilder.buildUserPrompt(request, context, plan, profession);
        BrainResponse raw = modelClient.answer(prompt, request.history());
        String processed = answerPostProcessor.process(raw.content(), plan, context);
        return new BrainResponse(processed, raw.tokens());
    }

    @Override
    public SseEmitter handleStream(BrainRequest request, ContextPlan plan) {
        var profession = professionClassifier.classify(request.message());
        DocumentContext context = retrievalStrategyRouter.retrieve(request, plan);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitThinking(emitter, buildClassifyEvent(plan, profession));
        emitThinking(emitter, buildRetrieveEvent(context));

        if (request.hasDocumentId() && context.unavailable()) {
            sendDocumentUnavailableMessage(emitter, context.unavailableReason());
            return emitter;
        }

        String prompt = promptBuilder.buildUserPrompt(request, context, plan, profession);
        log.info("Starting streaming for user {} with task {}", request.userId(), plan.taskType());

        StringBuilder accumulatedAnswer = new StringBuilder();

        modelClient.streamAnswer(prompt, request.history())
                .subscribe(
                        chunk -> {
                            try {
                                accumulatedAnswer.append(chunk);
                                emitter.send(SseEmitter.event().data(chunk));
                            } catch (IOException e) {
                                log.debug("Client disconnected while streaming: {}", e.getMessage());
                                emitter.completeWithError(e);
                            }
                        },
                        e -> {
                            log.error("Pipeline streaming error: {}", e.getMessage());
                            try {
                                emitter.send(SseEmitter.event().name("error").data("Ошибка при получении ответа от ИИ: " + e.getMessage()));
                            } catch (IOException ignored) {}
                            emitter.completeWithError(e);
                        },
                        () -> {
                            String finalAnswer = accumulatedAnswer.toString();
                            boolean grounded = hallucinationDetector.hasGroundingContext(context);
                            if (grounded) {
                                var ungrounded = hallucinationDetector.getUngroundedArticles(finalAnswer, context);
                                if (!ungrounded.isEmpty()) {
                                    log.warn("Stream redaction: {} ungrounded article(s) {}", ungrounded.size(), ungrounded);
                                    String redacted = hallucinationDetector.redactUngroundedArticles(finalAnswer, ungrounded);
                                    String list = ungrounded.stream().map(String::valueOf)
                                            .collect(Collectors.joining(", "));
                                    String footer = "\n\n> Не удалось подтвердить номера статей по retrieval-контексту: **"
                                            + list + "**. Перепроверьте по официальному источнику — модель могла перепутать раздел кодекса.";
                                    try {
                                        emitter.send(SseEmitter.event().name("redaction").data(redacted + footer));
                                    } catch (IOException ignored) {}
                                }
                            } else if (hallucinationDetector.hasLawReferences(finalAnswer)) {
                                String footer = "\n\n> ℹ️ Это аналитический ответ без обращения к retrieval-базе. "
                                        + "Перед использованием в правовом документе перепроверьте указанные нормы и номера статей по официальному источнику (adilet.zan.kz).";
                                try {
                                    emitter.send(SseEmitter.event().name("redaction").data(finalAnswer + footer));
                                } catch (IOException ignored) {}
                            }

                            log.info("Streaming completed successfully for user {}", request.userId());
                            emitter.complete();
                        }
                );
        return emitter;
    }

    private String documentUnavailableMessage(String reason) {
        return "Не удалось обработать прикреплённый документ: " + reason
                + " Попробуйте отправить вопрос ещё раз через несколько секунд "
                + "(индексация занимает время) или прикрепите документ повторно.";
    }

    private void sendDocumentUnavailableMessage(SseEmitter emitter, String reason) {
        try {
            emitter.send(SseEmitter.event().data(documentUnavailableMessage(reason)));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void emitThinking(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().name("thinking").data(data));
        } catch (IOException e) {
            log.debug("Could not send thinking event: {}", e.getMessage());
        }
    }

    private String buildClassifyEvent(ContextPlan plan, com.jvmd.llmbrainservice.service.pipeline.ProfessionProfile profession) {
        return "{\"step\":\"classify\",\"task\":\"" + plan.taskType()
                + "\",\"retrieval\":\"" + plan.retrievalMode()
                + "\",\"profession\":\"" + profession.name() + "\"}";
    }

    private String buildRetrieveEvent(DocumentContext context) {
        int chunks = (context != null && context.citations() != null) ? context.citations().size() : 0;
        return "{\"step\":\"retrieve\",\"found\":" + chunks + "}";
    }
}
