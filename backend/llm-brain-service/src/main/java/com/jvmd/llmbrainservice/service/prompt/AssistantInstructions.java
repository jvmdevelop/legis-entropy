package com.jvmd.llmbrainservice.service.prompt;

import java.util.List;

public interface AssistantInstructions {

    String systemPrompt();

    String documentAnalysisInstruction();

    default String generalAdviceInstruction() {
        return "Ответь на вопрос пользователя как юридический эксперт. Отвечай на русском, лаконично и по существу. " +
                "Если не хватает данных — скажи об этом и порекомендуй, куда обратиться.";
    }

    default List<FewShotExample> fewShotExamples() {
        return List.of();
    }
}
