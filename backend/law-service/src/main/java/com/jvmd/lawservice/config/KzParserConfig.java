package com.jvmd.lawservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

@Configuration
public class KzParserConfig {

    @Bean("kzPathQueue")
    public BlockingDeque<String> kzPathQueue() {
        return new LinkedBlockingDeque<>(1000);
    }
}
