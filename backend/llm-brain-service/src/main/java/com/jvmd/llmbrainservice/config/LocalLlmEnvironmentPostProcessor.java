package com.jvmd.llmbrainservice.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class LocalLlmEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered
{

    private static final String SOURCE_NAME = "localLlmOverride";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment env,
            @NonNull SpringApplication application
    ) {
        String enabled = env.getProperty("LOCAL_LLM_ENABLED", "false");
        if (!"true".equalsIgnoreCase(enabled)) return;

        String baseUrl = env.getProperty(
            "LOCAL_LLM_BASE_URL",
            "http://ollama:11434/v1"
        );
        String model = env.getProperty("LOCAL_LLM_MODEL", "llama3.2:3b");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.ai.openai.api-key", "ollama");
        props.put("spring.ai.openai.base-url", baseUrl);
        props.put("spring.ai.openai.chat.options.model", model);

        env.getPropertySources().addFirst(
            new MapPropertySource(SOURCE_NAME, props)
        );
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }
}
