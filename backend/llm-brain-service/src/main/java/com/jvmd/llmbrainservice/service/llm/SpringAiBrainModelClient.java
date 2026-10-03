package com.jvmd.llmbrainservice.service.llm;

import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.model.HistoryEntry;
import com.jvmd.llmbrainservice.service.prompt.AssistantInstructions;
import com.jvmd.llmbrainservice.service.prompt.FewShotExample;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class SpringAiBrainModelClient implements BrainModelClient {

    private final ChatClient chatClient;
    private final List<Message> fewShotMessages;
    private final ToolCallbackProvider toolCallbacks;

    public SpringAiBrainModelClient(
        ChatClient.Builder builder,
        AssistantInstructions instructions,
        ToolCallbackProvider toolCallbacks
    ) {
        this.chatClient = builder
            .defaultSystem(instructions.systemPrompt())
            .build();
        this.fewShotMessages = buildFewShotMessages(
            instructions.fewShotExamples()
        );
        this.toolCallbacks = toolCallbacks;
    }

    private static List<Message> buildFewShotMessages(
        List<FewShotExample> examples
    ) {
        if (examples == null || examples.isEmpty()) return List.of();
        List<Message> messages = new ArrayList<>(examples.size() * 2);
        for (FewShotExample ex : examples) {
            messages.add(new UserMessage(ex.user()));
            messages.add(new AssistantMessage(ex.assistant()));
        }
        return List.copyOf(messages);
    }

    @Override
    public BrainResponse answer(String userPrompt, List<HistoryEntry> history) {
        List<Message> historyMessages = toSpringAiMessages(history);
        ChatResponse response;
        try {
            response = chatClient
                .prompt()
                .messages(historyMessages)
                .user(userPrompt)
                .tools(toolCallbacks)
                .call()
                .chatResponse();
        } catch (RuntimeException ex) {
            log.warn(
                "LLM tool call failed ({}): {}. Falling back without tools.",
                ex.getClass().getSimpleName(),
                ex.getMessage()
            );
            try {
                response = chatClient
                    .prompt()
                    .messages(historyMessages)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
            } catch (RuntimeException fallbackException) {
                log.error(
                    "LLM fallback failed ({}): {}",
                    fallbackException.getClass().getSimpleName(),
                    fallbackException.getMessage()
                );
                return new BrainResponse("Не удалось получить ответ от LLM.", 0);
            }
        }
        return toBrainResponse(response);
    }

    @Override
    public Flux<String> streamAnswer(
        String userPrompt,
        List<HistoryEntry> history
    ) {
        List<Message> historyMessages = toSpringAiMessages(history);
        return chatClient
            .prompt()
            .messages(historyMessages)
            .user(userPrompt)
            .tools(toolCallbacks)
            .stream()
            .content()
            .filter(chunk -> chunk != null && !chunk.isEmpty())
            .doOnError(e ->
                log.error(
                    "Streaming error in LLM client: {}",
                    e.getMessage(),
                    e
                )
            );
    }

    private List<Message> toSpringAiMessages(List<HistoryEntry> history) {
        if (history == null || history.isEmpty()) {
            return fewShotMessages;
        }
        return history
            .stream()
            .map(h ->
                "USER".equalsIgnoreCase(h.role())
                    ? (Message) new UserMessage(h.content())
                    : (Message) new AssistantMessage(h.content())
            )
            .toList();
    }

    private BrainResponse toBrainResponse(ChatResponse response) {
        String content = "";
        int tokens = 0;
        if (response != null) {
            if (
                response.getResult() != null &&
                response.getResult().getOutput() != null
            ) {
                content = Objects.requireNonNullElse(
                    response.getResult().getOutput().getText(),
                    ""
                );
            }
            if (
                response.getMetadata() != null &&
                response.getMetadata().getUsage() != null
            ) {
                Integer total = response
                    .getMetadata()
                    .getUsage()
                    .getTotalTokens();
                tokens = total != null ? total : 0;
            }
        }
        return new BrainResponse(content, tokens);
    }
}
