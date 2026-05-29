package com.jvmd.dms.law.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

@Configuration
@EnableScheduling
public class KzParserConfig {

    @Bean("kzPathQueue")
    public BlockingDeque<String> kzPathQueue() {
        return new LinkedBlockingDeque<>(1000);
    }

}
