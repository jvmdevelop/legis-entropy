package com.jvmd.llmbrainservice.service.contract.detector;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@Order(20)
public class RentContractTypeDetector implements ContractTypeDetector {

    @Override
    public Optional<String> detect(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("арен") || lower.contains("найм") || lower.contains("арендатор")) {
            return Optional.of("аренды");
        }
        return Optional.empty();
    }
}
