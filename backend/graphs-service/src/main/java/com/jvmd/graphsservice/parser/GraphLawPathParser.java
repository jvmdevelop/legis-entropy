package com.jvmd.graphsservice.parser;

import java.util.ArrayList;
import java.util.List;

public abstract class GraphLawPathParser {

    public abstract List<String> parse(String url);

    public List<String> parseMultiplePages(String searchUrl, int maxPages) {
        List<String> allUrls = new ArrayList<>();

        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = buildPageUrl(searchUrl, page);
            List<String> pageUrls = parse(pageUrl);
            allUrls.addAll(pageUrls);

            if (pageUrls.isEmpty()) {
                break;
            }
        }

        return allUrls;
    }

    protected String buildPageUrl(String searchUrl, int page) {
        String separator = searchUrl.endsWith("/") ? "" : "/";
        return searchUrl + separator + "page=" + page + "&pagesize=100";
    }
}
