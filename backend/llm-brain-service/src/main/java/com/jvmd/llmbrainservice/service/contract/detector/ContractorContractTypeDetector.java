package com.jvmd.llmbrainservice.service.contract.detector;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@Order(50)
public class ContractorContractTypeDetector implements ContractTypeDetector {

    @Override
    public Optional<String> detect(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("подряд") || lower.contains("подрядчик")) {
            return Optional.of("подряда");
        }
        return Optional.empty();
    }
}
