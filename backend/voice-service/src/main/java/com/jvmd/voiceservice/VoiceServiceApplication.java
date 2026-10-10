package com.jvmd.voiceservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ThreadPoolExecutor;

@SpringBootApplication
@EnableScheduling
public class VoiceServiceApplication {

    @Bean(name = "voiceProcessingExecutor")
    public TaskExecutor voiceProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("VoiceProcessing-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }

    public static void main(String[] args) {
        SpringApplication.run(VoiceServiceApplication.class, args);
    }
}
