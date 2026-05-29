package com.jvmd.graphsservice.parser;

import com.jvmd.graphsservice.model.Article;
import com.jvmd.graphsservice.model.Provenance;
import com.jvmd.graphsservice.model.ProvenanceSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ArticleExtractor {

    private static final Pattern ARTICLE_HEADER = Pattern.compile(
        "(?:^|[\\s\\p{Punct}])Статья\\s+(\\d+(?:[-\\u2011]\\d+)?)(?:\\.|(?=\\s))"
    );

    private static final int MAX_ARTICLES = 5000;
    private static final int MAX_BODY_LENGTH = 50_000;

    public List<Article> extract(
        String lawCode,
        String country,
        String fullText,
        String sourceUri
    ) {
        List<Article> articles = new ArrayList<>();
        if (fullText == null || fullText.isBlank() || lawCode == null) {
            return articles;
        }

        String normalised = fullText
            .replace(' ', ' ')
            .replaceAll("[ \\t]+", " ");

        Matcher m = ARTICLE_HEADER.matcher(normalised);

        record Header(int matchStart, int afterHeader, String number) {}
        List<Header> headers = new ArrayList<>();
        while (m.find()) {
            int headerStart = m.start();
            String matched = m.group(0);
            int skip = matched.startsWith("Статья") ? 0 : 1;
            headers.add(
                new Header(headerStart + skip, m.end(), m.group(1).trim())
            );
            if (headers.size() > MAX_ARTICLES) {
                log.warn(
                    "Article extraction capped at {} for law {}",
                    MAX_ARTICLES,
                    lawCode
                );
                break;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < headers.size(); i++) {
            Header h = headers.get(i);
            int bodyEnd = (i + 1 < headers.size())
                ? headers.get(i + 1).matchStart()
                : normalised.length();
            String segment = normalised
                .substring(h.afterHeader(), bodyEnd)
                .trim();
            if (segment.length() > MAX_BODY_LENGTH) {
                segment = segment.substring(0, MAX_BODY_LENGTH);
            }
            String title = null;
            String body = segment;
            Matcher firstNumbered = Pattern.compile("\\s+1\\.\\s").matcher(
                segment
            );
            if (
                firstNumbered.find() &&
                firstNumbered.start() > 0 &&
                firstNumbered.start() < 300
            ) {
                title = segment.substring(0, firstNumbered.start()).trim();
                body = segment.substring(firstNumbered.start()).trim();
            } else {
                int dot = segment.indexOf('.');
                if (dot > 0 && dot < 200) {
                    title = segment.substring(0, dot).trim();
                    body = segment.substring(dot + 1).trim();
                }
            }
            if (
                title != null && (title.isBlank() || title.length() < 3)
            ) title = null;

            Article a = new Article(lawCode, country, h.number(), title, body);
            a.setRecordedAt(now);
            Provenance p = Provenance.govParser(sourceUri);
            p.setExtractedAt(now);
            a.applyProvenance(p);
            articles.add(a);
        }

        log.info(
            "Extracted {} articles from law {} ({})",
            articles.size(),
            lawCode,
            country
        );
        return articles;
    }
}
