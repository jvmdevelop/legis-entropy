package com.jvmd.graphsservice.controller;

import com.jvmd.graphsservice.parser.GraphKzParserManager;
import com.jvmd.graphsservice.repository.LawRepository;
import com.jvmd.graphsservice.service.ArticleService;
import com.jvmd.graphsservice.service.MetadataBackfillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph/parsing")
@Slf4j
public class ParsingController {

    private final GraphKzParserManager graphKzParserManager;
    private final ArticleService articleService;
    private final MetadataBackfillService metadataBackfillService;
    private final LawRepository lawRepository;

    public ParsingController(GraphKzParserManager graphKzParserManager,
                             ArticleService articleService,
                             MetadataBackfillService metadataBackfillService,
                             LawRepository lawRepository) {
        this.graphKzParserManager = graphKzParserManager;
        this.articleService = articleService;
        this.metadataBackfillService = metadataBackfillService;
        this.lawRepository = lawRepository;
    }

    @PostMapping("/trigger-discovery")
    public String triggerDiscovery() {
        log.info("Manual trigger of document discovery");
        graphKzParserManager.discoverDocuments();
        return "Document discovery triggered";
    }

    @PostMapping("/trigger-parsing")
    public String triggerParsing() {
        log.info("Manual trigger of document parsing");
        graphKzParserManager.parseDocuments();
        return "Document parsing triggered";
    }

    @PostMapping("/parse-url")
    public String parseUrl(@RequestParam String url) {
        log.info("Manual parsing of URL: {}", url);
        graphKzParserManager.parseDocumentAsync(url);
        return "URL parsing started asynchronously: " + url;
    }

    @GetMapping("/queue-status")
    public String getQueueStatus() {
        return graphKzParserManager.getQueueStatus();
    }

    @PostMapping("/backfill-metadata")
    public Object backfillMetadata(
            @RequestParam(required = false) String code,
            @RequestParam(required = false, defaultValue = "RK") String country,
            @RequestParam(required = false, defaultValue = "0") int limit,
            @RequestParam(required = false, defaultValue = "true") boolean async) {
        log.info("Backfill metadata triggered: code={}, country={}, limit={}, async={}", code, country, limit, async);
        if (code != null && !code.isBlank()) {
            return metadataBackfillService.backfillOne(code, country);
        }
        if (async) {
            return metadataBackfillService.startAsync(country, limit);
        }
        return metadataBackfillService.backfillAll(country, limit);
    }

    @GetMapping("/backfill-status")
    public String backfillStatus() {
        return metadataBackfillService.getStatus();
    }

    @PostMapping("/purge-expired")
    public String purgeExpired(@RequestParam(required = false, defaultValue = "RK") String country) {
        Long removed = lawRepository.purgeExpiredByCountry(country);
        long n = removed == null ? 0L : removed;
        log.info("Purged {} expired/invalid laws for country={}", n, country);
        return String.format("Purged %d expired/invalid laws for country=%s", n, country);
    }

    @PostMapping("/backfill-articles")
    public String backfillArticles(@RequestParam(required = false) String code,
                                   @RequestParam(required = false, defaultValue = "RK") String country) {
        if (code != null && !code.isBlank()) {
            int n = articleService.backfillForLaw(code, country);
            log.info("Backfilled {} articles for law {} ({})", n, code, country);
            return String.format("Backfilled %d articles for law %s (%s)", n, code, country);
        }
        int n = articleService.backfillAll(country);
        log.info("Backfilled {} articles for all laws (country={})", n, country);
        return String.format("Backfilled %d articles across all laws (country=%s)", n, country);
    }
}
