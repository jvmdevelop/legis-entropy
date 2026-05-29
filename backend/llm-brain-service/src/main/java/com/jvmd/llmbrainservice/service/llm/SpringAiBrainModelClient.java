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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class SpringAiBrainModelClient implements BrainModelClient {

    private static final String[] TOOL_NAMES = {
        "searchLaws",
        "searchUserDocuments",
        "getLawGraph",
        "findRelatedLaws",
        "addLawToUserGraph",
        "findAndAddRelatedLawsToGraph",
        "linkDocumentToLawsInGraph",
        "findArticle",
        "searchArticles",
        "linkDocumentClauseToArticle",
        "flagArticleConflict",
        "flagDocumentArticleConflict",
    };

    private final ChatClient chatClient;
    private final List<Message> fewShotMessages;

    public SpringAiBrainModelClient(
        ChatClient.Builder builder,
        AssistantInstructions instructions
    ) {
        this.chatClient = builder
            .defaultSystem(instructions.systemPrompt())
            .build();
        this.fewShotMessages = buildFewShotMessages(
            instructions.fewShotExamples()
        );
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
                .toolNames(TOOL_NAMES)
                .call()
                .chatResponse();
        } catch (RuntimeException ex) {
            log.warn(
                "LLM tool call failed: {}. Retrying with tools again before falling back.",
                ex.getMessage()
            );
            try {
                Thread.sleep(500);
                response = chatClient
                    .prompt()
                    .messages(historyMessages)
                    .user(userPrompt)
                    .toolNames(TOOL_NAMES)
                    .call()
                    .chatResponse();
            } catch (RuntimeException ex2) {
                log.warn(
                    "Second tool call failed, disabling tools for this request: {}",
                    ex2.getMessage()
                );
                response = chatClient
                    .prompt()
                    .messages(historyMessages)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Tool retry interrupted: {}", ie.getMessage());
                response = chatClient
                    .prompt()
                    .messages(historyMessages)
                    .user(userPrompt)
                    .call()
                    .chatResponse();
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
            .toolNames(TOOL_NAMES)
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
