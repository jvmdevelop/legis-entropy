package kz.legis.entropy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    /**
     * Rust service only does SQLite reads — 30 s is more than enough.
     * Buffer sized to 32 MB: graph JSON grows as the corpus expands.
     */
    @Bean("rustWebClient")
    public WebClient rustWebClient(@Value("${services.rust.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(Duration.ofSeconds(30))
                ))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                .build();
    }

    /**
     * ML service runs Qwen2 inference on CPU — allow up to 3 minutes for
     * LLM review generation before giving up.
     */
    @Bean("mlWebClient")
    public WebClient mlWebClient(@Value("${services.ml.base-url}") String baseUrl) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(Duration.ofMinutes(3))
                ))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }
}
