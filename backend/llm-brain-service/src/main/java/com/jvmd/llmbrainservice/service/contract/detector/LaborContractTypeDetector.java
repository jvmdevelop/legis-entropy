package com.jvmd.llmbrainservice.service.contract.detector;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@Order(10)
public class LaborContractTypeDetector implements ContractTypeDetector {

    @Override
    public Optional<String> detect(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("трудов") || lower.contains("работник") || lower.contains("работодатель")) {
            return Optional.of("трудовой");
        }
        return Optional.empty();
    }
}
