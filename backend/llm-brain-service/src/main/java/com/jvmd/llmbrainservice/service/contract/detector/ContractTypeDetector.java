package com.jvmd.llmbrainservice.service.contract.detector;

import java.util.Optional;

public interface ContractTypeDetector {
    Optional<String> detect(String text);
}
