package com.jvmd.llmbrainservice.service.contract.detector;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@Order(40)
public class ServicesContractTypeDetector implements ContractTypeDetector {

    @Override
    public Optional<String> detect(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("услуг") || lower.contains("исполнитель") || lower.contains("заказчик")) {
            return Optional.of("услуг");
        }
        return Optional.empty();
    }
}
