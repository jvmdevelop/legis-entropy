package com.jvmd.llmbrainservice.service.llm;

import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.model.HistoryEntry;
import reactor.core.publisher.Flux;

import java.util.List;

public interface BrainModelClient {

    BrainResponse answer(String userPrompt, List<HistoryEntry> history);

    Flux<String> streamAnswer(String userPrompt, List<HistoryEntry> history);
}
