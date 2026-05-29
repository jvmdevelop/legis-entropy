package com.jvmd.graphsservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

@Configuration
@EnableScheduling
public class ParserConfiguration {

    @Bean(name = "graphKzPathQueue")
    public BlockingDeque<String> graphKzPathQueue() {
        return new LinkedBlockingDeque<>(10000);
    }
}
