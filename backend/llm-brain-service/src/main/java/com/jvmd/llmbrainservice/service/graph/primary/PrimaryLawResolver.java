package com.jvmd.llmbrainservice.service.graph.primary;

import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.dto.LawInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class PrimaryLawResolver {

    private final List<PrimaryLawSource> sources;
    private final GraphServiceClient graphServiceClient;

    public LinkedHashMap<String, LawInfo> resolve(PrimaryLawContext seedCtx) {
        LinkedHashMap<String, LawInfo> hits = new LinkedHashMap<>();
        var sorted = sources.stream()
                .sorted(Comparator.comparingInt(PrimaryLawSource::order))
                .toList();

        for (PrimaryLawSource src : sorted) {
            PrimaryLawContext ctx = seedCtx.withAlready(hits);
            if (!src.shouldRun(ctx)) {
                log.debug("PrimaryLawResolver skip {}: shouldRun=false (have {} hit(s))",
                        src.name(), hits.size());
                continue;
            }
            try {
                List<LawInfo> contributed = src.collect(ctx);
                int before = hits.size();
                for (LawInfo law : contributed) {
                    mergeLaw(hits, law, ctx.country());
                }
                log.info("PrimaryLawResolver {} contributed {} new law(s); total={}",
                        src.name(), hits.size() - before, hits.size());
            } catch (Exception e) {
                log.warn("PrimaryLawResolver source {} failed: {}", src.name(), e.getMessage());
            }
            if (hits.size() >= ctx.targetCount() * 3) break;
        }
        return hits;
    }

    private void mergeLaw(LinkedHashMap<String, LawInfo> hits, LawInfo law, String country) {
        if (law == null || law.code() == null || law.code().isBlank()) return;
        String key = law.code();
        if (hits.containsKey(key)) return;
        LawInfo resolved = law;
        boolean hasMetadata = law.title() != null && !law.title().equalsIgnoreCase(law.code());
        if (!hasMetadata) {
            try {
                resolved = graphServiceClient.searchLaws(law.code(), country).stream()
                        .filter(l -> law.code().equalsIgnoreCase(l.code()))
                        .findFirst()
                        .orElse(law);
            } catch (Exception e) {
                log.debug("metadata lookup failed for {}: {}", law.code(), e.getMessage());
            }
        }
        hits.put(canonicalize(key), resolved);
    }

    private static String canonicalize(String code) {
        return code.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
