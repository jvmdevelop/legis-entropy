package com.jvmd.dms.law.parser;

import com.jvmd.dms.law.parser.impl.LawParser;
import com.jvmd.dms.law.parser.impl.LawPathParser;
import com.jvmd.dms.law.storage.CategorizedLawStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class LawParserManager<T extends LawParser> {

    protected String baseUrl;
    protected String searchPath;
    protected String searchUrl;

    protected final T lawParser;
    protected final LawPathParser pathParser;
    protected final CategorizedLawStorage categorizedLawStorage;
    protected final BlockingDeque<String> pathQueue;
    protected final ExecutorService executorService;

    public LawParserManager(T lawParser,
                           LawPathParser pathParser,
                           CategorizedLawStorage categorizedLawStorage,
                           BlockingDeque<String> pathQueue,
                           String baseUrl,
                           String searchPath) {
        this.lawParser = lawParser;
        this.pathParser = pathParser;
        this.categorizedLawStorage = categorizedLawStorage;
        this.pathQueue = pathQueue;
        this.baseUrl = baseUrl;
        this.searchPath = searchPath;
        this.executorService = Executors.newFixedThreadPool(3);
        initSearchUrl();
    }

    protected void initSearchUrl() {
        if (baseUrl != null && !baseUrl.isEmpty() && searchPath != null && !searchPath.isEmpty()) {
            this.searchUrl = baseUrl + searchPath;
            log.info("LawParserManager initialized with search URL: {}", searchUrl);
        } else {
            log.warn("Base URL or search path not configured for LawParserManager");
        }
    }

    @Scheduled(fixedRate = 300000)
    public void discoverDocuments() {
        if (searchUrl == null || searchUrl.isEmpty()) {
            log.warn("Search URL not configured, skipping document discovery");
            return;
        }

        executorService.submit(() -> {
            try {
                log.info("Discovering documents...");

                var documentUrls = pathParser.parseMultiplePages(searchUrl, 5);

                for (String documentUrl : documentUrls) {
                    if (!pathQueue.contains(documentUrl)) {
                        pathQueue.addLast(documentUrl);
                        log.debug("Added new document URL: {}", documentUrl);
                    }
                }

                log.info("Discovered {} documents, queue size: {}", documentUrls.size(), pathQueue.size());

            } catch (Exception e) {
                log.error("Error discovering documents: {}", e.getMessage(), e);
            }
        });
    }

    @Scheduled(fixedRate = 10000)
    public void parseDocuments() {
        if (pathQueue.isEmpty()) {
            return;
        }

        executorService.submit(() -> {
            try {
                String documentUrl = pathQueue.pollFirst();
                if (documentUrl != null) {
                    log.info("Parsing document: {}", documentUrl);

                    var law = lawParser.parse(documentUrl);
                    categorizedLawStorage.save(law);

                    log.info("Successfully parsed and saved law: {} (category: {}, country: {})",
                        law.getTitle(), law.getCategory(), law.getCountry());
                }

            } catch (Exception e) {
                log.error("Error parsing document: {}", e.getMessage(), e);
            }
        });
    }

    @Async
    public void parseDocumentAsync(String url) {
        try {
            log.info("Manual parsing of document: {}", url);
            var law = lawParser.parse(url);
            categorizedLawStorage.save(law);
            log.info("Successfully parsed and saved law: {} (category: {}, country: {})",
                law.getTitle(), law.getCategory(), law.getCountry());
        } catch (Exception e) {
            log.error("Error in manual parsing: {}", e.getMessage(), e);
        }
    }

    public String getQueueStatus() {
        return String.format("Parser queue size: %d", pathQueue.size());
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
