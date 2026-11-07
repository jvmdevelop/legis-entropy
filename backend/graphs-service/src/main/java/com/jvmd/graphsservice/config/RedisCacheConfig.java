package com.jvmd.graphsservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jvmd.graphsservice.dto.ArticleDTO;
import com.jvmd.graphsservice.dto.LawDTO;
import com.jvmd.graphsservice.dto.LawGraphResponse;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Each cache gets a serializer bound to its concrete value type.
 * No default typing: values like Stream.toList() (final ImmutableCollections.ListN)
 * were written without type ids and then failed to deserialize on cache hit.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        TypeFactory tf = mapper.getTypeFactory();

        JavaType lawList = tf.constructCollectionType(List.class, LawDTO.class);
        JavaType articleList = tf.constructCollectionType(List.class, ArticleDTO.class);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .disableCachingNullValues()
                .entryTtl(Duration.ofHours(24));

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                "lawGraph",      typed(base, mapper, tf.constructType(LawGraphResponse.class), 72),
                "lawSearch",     typed(base, mapper, lawList, 24),
                "relatedLaws",   typed(base, mapper, lawList, 72),
                "lawByCode",     typed(base, mapper, tf.constructType(LawDTO.class), 72),
                "articlesByLaw", typed(base, mapper, articleList, 72),
                "article",       typed(base, mapper, tf.constructType(ArticleDTO.class), 72),
                "articleSearch", typed(base, mapper, articleList, 24)
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    private static RedisCacheConfiguration typed(RedisCacheConfiguration base, ObjectMapper mapper,
                                                 JavaType type, long ttlHours) {
        return base
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(mapper, type)))
                .entryTtl(Duration.ofHours(ttlHours));
    }
}
