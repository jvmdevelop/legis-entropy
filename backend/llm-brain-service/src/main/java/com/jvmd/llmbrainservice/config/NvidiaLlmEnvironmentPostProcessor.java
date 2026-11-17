package com.jvmd.llmbrainservice.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class NvidiaLlmEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered
{

    private static final String SOURCE_NAME = "nvidiaLlmOverride";

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment env,
            @NonNull SpringApplication application
    ) {
        String enabled = env.getProperty("NVIDIA_LLM_ENABLED", "false");
        String apiKey = env.getProperty("NVIDIA_API_KEY", "");
        boolean hasApiKey = StringUtils.hasText(apiKey);
        if (!"true".equalsIgnoreCase(enabled) && !hasApiKey) return;

        String baseUrl = env.getProperty(
            "NVIDIA_LLM_BASE_URL",
            "https://integrate.api.nvidia.com/v1/"
        );
        String model = env.getProperty(
            "NVIDIA_LLM_MODEL",
            "nvidia/nemotron-3.5-super-vl-120b-a12b"
        );

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.ai.openai.api-key", apiKey);
        props.put("spring.ai.openai.base-url", baseUrl);
        props.put("spring.ai.openai.chat.options.model", model);
        props.put("brain.llm.structured.disable-thinking",
            env.getProperty("NVIDIA_STRUCTURED_DISABLE_THINKING", "true"));

        env.getPropertySources().addFirst(
            new MapPropertySource(SOURCE_NAME, props)
        );
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 20;
    }
}
