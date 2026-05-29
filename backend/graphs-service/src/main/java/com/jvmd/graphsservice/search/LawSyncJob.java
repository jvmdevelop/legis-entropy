package com.jvmd.graphsservice.search;

import com.jvmd.graphsservice.repository.LawRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LawSyncJob {

    private final LawRepository lawRepository;
    private final LawIndexService lawIndexService;

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void syncOnStartup() {
        log.info("LawSyncJob: starting Neo4j → ES sync...");
        int indexed = 0;
        int skipped = 0;
        try {
            for (var law : lawRepository.findAllShallow()) {
                if (LawIndexService.isIndexable(law)) {
                    lawIndexService.index(law);
                    indexed++;
                } else {
                    skipped++;
                }
            }
            log.info("LawSyncJob: done — indexed={}, skipped(low-force)={}", indexed, skipped);
        } catch (Exception e) {
            log.warn("LawSyncJob: sync failed (ES may not be ready yet): {}", e.getMessage());
        }
    }
}
