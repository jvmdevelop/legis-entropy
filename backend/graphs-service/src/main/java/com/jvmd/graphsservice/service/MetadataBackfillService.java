package com.jvmd.graphsservice.service;

import com.jvmd.graphsservice.model.Law;
import com.jvmd.graphsservice.parser.GraphKzLawParser;
import com.jvmd.graphsservice.repository.LawRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetadataBackfillService {

    private final LawRepository lawRepository;
    private final GraphKzLawParser parser;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicReference<BackfillResult> lastResult = new AtomicReference<>();
    private final AtomicReference<String> status = new AtomicReference<>("idle");

    public record BackfillResult(int considered, int updated, int skipped, int failed) {}

    public String startAsync(String country, int limit) {
        if ("running".equals(status.get())) {
            return "Backfill already running";
        }
        status.set("running");
        executor.submit(() -> {
            try {
                BackfillResult r = backfillAll(country, limit);
                lastResult.set(r);
                status.set("done");
            } catch (Exception e) {
                log.error("Backfill crashed", e);
                status.set("failed: " + e.getMessage());
            }
        });
        return "Backfill started (country=" + country + ", limit=" + limit + ")";
    }

    public String getStatus() {
        BackfillResult r = lastResult.get();
        return status.get() + (r == null ? "" : " | last: " + r);
    }

    public BackfillResult backfillAll(String country, int limit) {
        String ctry = country == null || country.isBlank() ? "RK" : country;
        List<String> uris = lawRepository.findSourceUrisByCountry(ctry);
        log.info("Metadata backfill start (country={}): {} URIs to consider", ctry, uris.size());
        AtomicInteger updated = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        int considered = 0;
        for (String uri : uris) {
            if (limit > 0 && considered >= limit) break;
            considered++;
            Law law = lawRepository.findBySourceUriAndCountry(uri, ctry).orElse(null);
            if (law == null) {
                skipped.incrementAndGet();
                continue;
            }
            try {
                String beforeCode = law.getCode();
                String beforeCitation = law.getCitationCode();
                parser.enrichMetadataInPlace(uri, law);
                if (sameStrings(beforeCode, law.getCode())
                        && sameStrings(beforeCitation, law.getCitationCode())) {
                    skipped.incrementAndGet();
                } else {
                    lawRepository.save(law);
                    updated.incrementAndGet();
                    log.debug("Backfilled: {} → code={}, citation={}",
                            uri, law.getCode(), law.getCitationCode());
                }
            } catch (Exception e) {
                log.warn("Failed to backfill {}: {}", uri, e.getMessage());
                failed.incrementAndGet();
            }
            if (considered % 200 == 0) {
                log.info("Backfill progress: {}/{}, updated={}, skipped={}, failed={}",
                        considered, uris.size(), updated.get(), skipped.get(), failed.get());
            }
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.info("Metadata backfill done (country={}): considered={}, updated={}, skipped={}, failed={}",
                ctry, considered, updated.get(), skipped.get(), failed.get());
        return new BackfillResult(considered, updated.get(), skipped.get(), failed.get());
    }

    public BackfillResult backfillOne(String code, String country) {
        String ctry = country == null || country.isBlank() ? "RK" : country;
        return lawRepository.findByCodeAndCountry(code, ctry)
                .map(law -> {
                    if (law.getSourceUri() == null) {
                        return new BackfillResult(1, 0, 1, 0);
                    }
                    try {
                        parser.enrichMetadataInPlace(law.getSourceUri(), law);
                        lawRepository.save(law);
                        return new BackfillResult(1, 1, 0, 0);
                    } catch (Exception e) {
                        log.warn("Failed to backfill {}: {}", code, e.getMessage());
                        return new BackfillResult(1, 0, 0, 1);
                    }
                })
                .orElse(new BackfillResult(0, 0, 0, 0));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private static boolean sameStrings(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
