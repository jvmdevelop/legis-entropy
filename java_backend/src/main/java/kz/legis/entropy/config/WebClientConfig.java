package kz.legis.entropy.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean("rustWebClient")
    public WebClient rustWebClient(
        @Value("${services.rust.base-url}") String baseUrl
    ) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(
                new ReactorClientHttpConnector(
                    HttpClient.create().responseTimeout(Duration.ofSeconds(30))
                )
            )
            .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
            .build();
    }

    @Bean("mlWebClient")
    public WebClient mlWebClient(
        @Value("${services.ml.base-url}") String baseUrl
    ) {
        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(
                new ReactorClientHttpConnector(
                    HttpClient.create().responseTimeout(Duration.ofMinutes(3))
                )
            )
            .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    }
}
