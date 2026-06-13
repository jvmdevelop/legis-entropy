package com.jvmd.graphsservice.parser;

import com.jvmd.graphsservice.model.Article;
import com.jvmd.graphsservice.model.Law;
import com.jvmd.graphsservice.repository.LawRepository;
import com.jvmd.graphsservice.search.LawIndexService;
import com.jvmd.graphsservice.service.ArticleService;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public abstract class GraphLawParserManager<T extends Parser> {

    protected String baseUrl;
    protected String searchPath;
    protected String searchUrl;

    protected final T lawParser;
    protected final GraphLawPathParser pathParser;
    protected final LawRepository lawRepository;
    protected final BlockingDeque<String> pathQueue;
    protected final ExecutorService executorService;
    protected final Semaphore parserPermits = new Semaphore(5);

    @Autowired(required = false)
    protected ArticleExtractor articleExtractor;

    @Autowired(required = false)
    protected ArticleService articleService;

    @Autowired(required = false)
    protected LawIndexService lawIndexService;

    @Value("${parser.skip-expired:true}")
    protected boolean skipExpired;

    protected boolean shouldSkipLaw(Law law) {
        if (law == null) return true;
        if (!skipExpired) return false;
        String s = law.getStatus();
        if (s == null) return false;
        return (
            "EXPIRED".equalsIgnoreCase(s) ||
            "INVALID".equalsIgnoreCase(s) ||
            "PARSE_ERROR".equalsIgnoreCase(s)
        );
    }

    public GraphLawParserManager(
        T lawParser,
        GraphLawPathParser pathParser,
        LawRepository lawRepository,
        BlockingDeque<String> pathQueue,
        String baseUrl,
        String searchPath
    ) {
        this.lawParser = lawParser;
        this.pathParser = pathParser;
        this.lawRepository = lawRepository;
        this.pathQueue = pathQueue;
        this.baseUrl = baseUrl;
        this.searchPath = searchPath;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        initSearchUrl();
    }

    protected void extractAndPersistArticles(Law law, String sourceUri) {
        if (articleExtractor == null || articleService == null) return;
        if (
            law == null || law.getCode() == null || law.getSummary() == null
        ) return;
        try {
            List<Article> articles = articleExtractor.extract(
                law.getCode(),
                law.getCountry(),
                law.getSummary(),
                sourceUri
            );
            if (!articles.isEmpty()) {
                articleService.persistAll(articles);
            }
        } catch (Exception e) {
            log.warn(
                "Article extraction failed for {} {}: {}",
                law.getCode(),
                law.getCountry(),
                e.getMessage()
            );
        }
    }

    protected void persistStructured(
        Law law,
        LawStructure.ExtractionResult structure,
        String sourceUri
    ) {
        if (law == null || law.getCode() == null) return;
        if (structure == null || structure.articles().isEmpty()) {
            extractAndPersistArticles(law, sourceUri);
            return;
        }
        if (articleService != null) {
            articleService.persistEnriched(structure.articles());
            articleService.persistAmendments(
                law.getCode(),
                law.getCountry(),
                structure.amendments()
            );
            articleService.persistCrossRefs(
                law.getCode(),
                law.getCountry(),
                structure.crossRefs()
            );
        }
    }

    protected void initSearchUrl() {
        if (
            baseUrl != null &&
            !baseUrl.isEmpty() &&
            searchPath != null &&
            !searchPath.isEmpty()
        ) {
            this.searchUrl = baseUrl + searchPath;
            log.info(
                "GraphLawParserManager initialized with search URL: {}",
                searchUrl
            );
        } else {
            log.warn(
                "Base URL or search path not configured for GraphLawParserManager"
            );
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
                parserPermits.acquire();
                try {
                    log.info("Discovering documents...");

                    var documentUrls = pathParser.parseMultiplePages(searchUrl, 5);

                    for (String documentUrl : documentUrls) {
                        if (!pathQueue.contains(documentUrl)) {
                            pathQueue.addLast(documentUrl);
                            log.debug("Added new document URL: {}", documentUrl);
                        }
                    }

                    log.info(
                        "Discovered {} documents, queue size: {}",
                        documentUrls.size(),
                        pathQueue.size()
                    );
                } finally {
                    parserPermits.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Document discovery interrupted");
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
                parserPermits.acquire();
                try {
                    String documentUrl = pathQueue.pollFirst();
                    if (documentUrl != null) {
                        log.info("Parsing document: {}", documentUrl);

                        Law law;
                        LawStructure.ExtractionResult structure = null;
                        Object parsed = lawParser.parse(documentUrl);
                        if (parsed instanceof Law l) {
                            law = l;
                        } else {
                            law = (Law) parsed;
                        }
                        if (lawParser instanceof GraphKzLawParser kzParser) {
                            var full = kzParser.parseFull(documentUrl);
                            law = full.law();
                            structure = full.structure();
                        }
                        if (law.getSource() == null) {
                            law.applyProvenance(
                                com.jvmd.graphsservice.model.Provenance.govParser(
                                    documentUrl
                                )
                            );
                        }
                        if (shouldSkipLaw(law)) {
                            log.info(
                                "Skipping non-current law {} (status={}, url={})",
                                law.getTitle(),
                                law.getStatus(),
                                documentUrl
                            );
                            return;
                        }
                        if (!LawIndexService.isIndexable(law)) {
                            log.debug(
                                "Skipping low-force act (parser): {} ({})",
                                law.getCode(),
                                law.getLegalForce()
                            );
                            return;
                        }
                        lawRepository.save(law);
                        if (lawIndexService != null) lawIndexService.index(law);
                        if (structure != null) {
                            persistStructured(law, structure, documentUrl);
                        } else {
                            extractAndPersistArticles(law, documentUrl);
                        }

                        log.info(
                            "Successfully parsed and saved law: {} (status: {}, country: {})",
                            law.getTitle(),
                            law.getStatus(),
                            law.getCountry()
                        );
                    }
                } finally {
                    parserPermits.release();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Document parsing interrupted");
            } catch (Exception e) {
                log.error("Error parsing document: {}", e.getMessage(), e);
            }
        });
    }

    @Async
    public void parseDocumentAsync(String url) {
        try {
            log.info("Manual parsing of document: {}", url);
            Law law;
            LawStructure.ExtractionResult structure = null;
            if (lawParser instanceof GraphKzLawParser kzParser) {
                var full = kzParser.parseFull(url);
                law = full.law();
                structure = full.structure();
            } else {
                law = (Law) lawParser.parse(url);
            }
            if (law.getSource() == null) {
                law.applyProvenance(
                    com.jvmd.graphsservice.model.Provenance.govParser(url)
                );
            }
            if (shouldSkipLaw(law)) {
                log.info(
                    "Skipping non-current law {} (status={}, url={})",
                    law.getTitle(),
                    law.getStatus(),
                    url
                );
                return;
            }
            if (!LawIndexService.isIndexable(law)) {
                log.debug(
                    "Skipping low-force act (manual parse): {} ({})",
                    law.getCode(),
                    law.getLegalForce()
                );
                return;
            }
            lawRepository.save(law);
            if (lawIndexService != null) lawIndexService.index(law);
            if (structure != null) {
                persistStructured(law, structure, url);
            } else {
                extractAndPersistArticles(law, url);
            }
            log.info(
                "Successfully parsed and saved law: {} (status: {}, country: {})",
                law.getTitle(),
                law.getStatus(),
                law.getCountry()
            );
        } catch (Exception e) {
            log.error("Error in manual parsing: {}", e.getMessage(), e);
        }
    }

    public String getQueueStatus() {
        return String.format("Parser queue size: %d", pathQueue.size());
    }

    @PreDestroy
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
