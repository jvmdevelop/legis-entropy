package com.jvmd.dms.law.parser.impl.impl;

import com.jvmd.dms.law.parser.impl.LawPathParser;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class KzLawPathParser extends LawPathParser {

    @Override
    public List<String> parse(String url) {
        Set<String> documentUrls = new LinkedHashSet<>();

        try {
            Document doc = AdiletJsoup.connect(url).get();
            Elements documentLinks = doc.select("a[href^=/rus/docs/], a[href^='/rus/docs/']");

            for (Element link : documentLinks) {
                String href = link.attr("href");
                if (isDocumentPath(href)) {
                    String fullUrl = toAbsoluteUrl(url, href);
                    documentUrls.add(fullUrl);
                    log.debug("Found KZ law document: {} -> {}", link.text(), fullUrl);
                }
            }

            if (documentUrls.isEmpty()) {
                Elements alternativeLinks = doc.select("a[href*='docs/']");
                for (Element link : alternativeLinks) {
                    String href = link.attr("href");
                    if (isDocumentPath(href)) {
                        String fullUrl = toAbsoluteUrl(url, href);
                        documentUrls.add(fullUrl);
                        log.debug("Found alternative KZ law document: {} -> {}", link.text(), fullUrl);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Error parsing KZ law URLs from {}: {}", url, e.getMessage());
        }

        return List.copyOf(documentUrls);
    }

    @Override
    public List<String> parseMultiplePages(String searchUrl, int maxPages) {
        Set<String> allUrls = new LinkedHashSet<>();

        for (int page = 1; page <= maxPages; page++) {
            String pageUrl = buildPageUrl(searchUrl, page);
            List<String> pageUrls = parse(pageUrl);
            allUrls.addAll(pageUrls);

            if (pageUrls.isEmpty()) {
                break;
            }
        }

        return List.copyOf(allUrls);
    }

    private boolean isDocumentPath(String href) {
        return href != null
                && href.matches("^/rus/docs/[A-Za-z0-9_]+$")
                && !href.endsWith("/rss");
    }

    private String toAbsoluteUrl(String sourceUrl, String href) {
        return URI.create(sourceUrl).resolve(href).toString();
    }
}
