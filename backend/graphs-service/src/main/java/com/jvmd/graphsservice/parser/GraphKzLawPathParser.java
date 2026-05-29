package com.jvmd.graphsservice.parser;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class GraphKzLawPathParser extends GraphLawPathParser {

    private static final Set<String> EXPIRED_STATUSES = Set.of(
        "yts",
        "vexp",
        "bak",
        "del",
        "err"
    );

    private static final List<String> DEFAULT_ACT_TYPES = List.of(
        "КОНС",
        "КЗАК",
        "КОД",
        "ЗАК",
        "УКАЗ",
        "НПОС"
    );

    private final ZanGovApi zanGovApi;

    @Override
    public List<String> parse(String url) {
        return parseMultiplePages(url, 1);
    }

    @Override
    public List<String> parseMultiplePages(String ignored, int maxPages) {
        Set<String> documentUrls = new LinkedHashSet<>();
        int collected = 0;
        for (int page = 1; page <= maxPages; page++) {
            Map<String, Object> query = new HashMap<>();
            query.put("page", page);
            query.put("limit", 50);
            query.put("lang", "rus");
            query.put("actTypes", DEFAULT_ACT_TYPES);
            try {
                JsonNode resp = zanGovApi.search(query);
                JsonNode list = resp.path("list");
                if (!list.isArray() || list.isEmpty()) break;
                for (JsonNode item : list) {
                    String status = item
                        .path("status")
                        .asText("")
                        .toLowerCase(Locale.ROOT);
                    if (EXPIRED_STATUSES.contains(status)) continue;
                    String id = item.path("id").asText(null);
                    if (id == null) continue;
                    String docUrl =
                        "https://zan.gov.kz/client/#!/doc/" + id + "/rus";
                    if (documentUrls.add(docUrl)) collected++;
                    log.debug(
                        "Discovered KZ document: {} (status={})",
                        docUrl,
                        status
                    );
                }
                int pageCount = resp.path("pageCount").asInt(0);
                if (page >= pageCount) break;
            } catch (Exception e) {
                log.warn(
                    "zan.gov.kz search page {} failed: {}",
                    page,
                    e.getMessage()
                );
                break;
            }
        }
        log.info(
            "zan.gov.kz discovery collected {} document URLs across {} pages",
            collected,
            maxPages
        );
        return new ArrayList<>(documentUrls);
    }
}
