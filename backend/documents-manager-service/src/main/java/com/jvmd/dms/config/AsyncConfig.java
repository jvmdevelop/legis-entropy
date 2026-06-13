package com.jvmd.dms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "lawParserExecutor")
    public Executor lawParserExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("LawParser-");
        executor.setVirtualThreads(true);
        return executor;
    }

    @Bean(name = "documentIngestionExecutor")
    public Executor documentIngestionExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("DocumentIngestion-");
        executor.setVirtualThreads(true);
        return executor;
    }
}
