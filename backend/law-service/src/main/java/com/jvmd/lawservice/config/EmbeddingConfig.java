package com.jvmd.lawservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@Slf4j
public class EmbeddingConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(
            @Value("${spring.ai.embedding.transformer.onnx.model-uri}") String modelUri,
            @Value("${spring.ai.embedding.transformer.onnx.tokenizer-uri}") String tokenizerUri,
            @Value("${spring.ai.embedding.transformer.resource-cache-dir:./embeddings-cache}") String cacheDir) {
        log.info("Embedding model: {}", modelUri);
        var model = new TransformersEmbeddingModel();
        model.setModelResource(modelUri);
        model.setTokenizerResource(tokenizerUri);
        model.setResourceCacheDirectory(cacheDir);
        return model;
    }
}
