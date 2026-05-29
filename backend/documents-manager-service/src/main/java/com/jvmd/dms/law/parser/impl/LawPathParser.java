package com.jvmd.dms.law.parser.impl;

import com.jvmd.dms.law.parser.Parser;

import java.util.ArrayList;
import java.util.List;

public abstract class LawPathParser implements Parser<List<String>> {

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
