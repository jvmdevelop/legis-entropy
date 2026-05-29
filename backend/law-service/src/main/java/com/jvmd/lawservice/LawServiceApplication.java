package com.jvmd.lawservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class LawServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LawServiceApplication.class, args);
    }
}
