package com.jvmd.lawservice.importer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final KzLawImporter kzLawImporter;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeData() {
        try {
            log.info("Initializing KZ law data...");
            kzLawImporter.importSampleKzLaws();
            log.info("Data initialization completed successfully");
        } catch (Exception e) {
            log.error("Error during data initialization", e);
        }
    }
}
