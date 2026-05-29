package com.jvmd.llmbrainservice.service.contract.detector;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
@Order(30)
public class SupplyContractTypeDetector implements ContractTypeDetector {

    @Override
    public Optional<String> detect(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("поставк") || lower.contains("покупатель")
                || (lower.contains("продавец") && lower.contains("товар"))) {
            return Optional.of("поставки");
        }
        return Optional.empty();
    }
}
