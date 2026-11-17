package com.jvmd.graphsservice.parser;

import com.jvmd.graphsservice.model.Law;
import com.jvmd.graphsservice.model.ProvenanceSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
@Slf4j
@RequiredArgsConstructor
public class AdiletDocParser {

    private final CitationCodeBuilder citationCodeBuilder;

    private static final Pattern DOC_REF = Pattern.compile(
        "(?:old\\.)?adilet\\.zan\\.kz/(rus|kaz|eng)/docs/([A-Z][0-9_.]+)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TITLE_TAG = Pattern.compile(
        "<title>(.*?)</title>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile(
        "<(script|style|head|noscript)\\b.*?</\\1>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern BLOCK_TAG_END = Pattern.compile(
        "</?(p|h[1-6]|li|tr|div|table|blockquote|pre|ul|ol|td|th|section|article|br)\\b[^>]*>",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private static final int MAX_SUMMARY = 200_000;

    private HttpClient httpClient;

    @jakarta.annotation.PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public boolean supports(String url) {
        return url != null && DOC_REF.matcher(url).find();
    }

    public Law parse(String url) {
        Matcher m = DOC_REF.matcher(url);
        if (!m.find()) {
            throw new IllegalArgumentException(
                "Cannot resolve adilet NGR from URL: " + url
            );
        }
        String lang = m.group(1).toLowerCase(Locale.ROOT);
        String ngr = m.group(2).toUpperCase(Locale.ROOT);
        String canonicalUrl =
            "https://old.adilet.zan.kz/" + lang + "/docs/" + ngr;
        try {
            String html = fetch(canonicalUrl);
            Law law = buildLaw(html, canonicalUrl, lang);
            log.info(
                "Parsed adilet law {}: {} (summary {} chars, status {})",
                ngr,
                law.getTitle(),
                law.getSummary() == null ? 0 : law.getSummary().length(),
                law.getStatus()
            );
            return law;
        } catch (Exception e) {
            log.warn(
                "Failed to parse adilet law {}: {}",
                canonicalUrl,
                e.getMessage()
            );
            Law errorLaw = new Law(
                "Parse Error: " + canonicalUrl,
                null,
                "RK",
                "PARSE_ERROR",
                "Failed to parse document: " + e.getMessage()
            );
            errorLaw.setStatus("INVALID");
            return errorLaw;
        }
    }

    private Law buildLaw(String html, String url, String lang) {
        String title = extractTitle(html);
        String status = html.contains("Утративший силу") ? "EXPIRED" : "ACTIVE";

        String formOfAct;
        String t = title.toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (t.startsWith("конституция")) formOfAct = "Конституция";
        else if (t.contains("кодекс")) formOfAct = "Кодекс";
        else formOfAct = "Закон";

        String citation = citationCodeBuilder.build(title, formOfAct, null, "RK");
        if (
            citation != null &&
            citation.equals("ГК РК") &&
            t.contains("(особенная часть)")
        ) {
            citation = "ГК РК (Особенная часть)";
        }

        Law law = new Law();
        law.setCountry("RK");
        law.setLanguage(lang);
        law.setSourceUri(url);
        law.setTitle(title);
        law.setStatus(status);
        law.setType(formOfAct);
        law.setFormOfAct(formOfAct);
        law.setLegalForce(formOfAct);
        law.setCitationCode(citation);
        law.setCode(citation);
        law.setSummary(extractSummary(html));
        law.setSource(ProvenanceSource.GOV_PARSER);
        law.setExtractedBy("adilet-doc-parser");
        law.setRecordedAt(LocalDateTime.now());
        return law;
    }

    private String extractTitle(String html) {
        Matcher m = TITLE_TAG.matcher(html);
        if (!m.find()) return "Untitled Document";
        String title = m.group(1).trim();
        int cut = title.indexOf(" - ИПС");
        if (cut > 0) title = title.substring(0, cut);
        return HtmlUtils.htmlUnescape(title).trim();
    }

    private String extractSummary(String html) {
        int body = html.indexOf("<body");
        String content = body >= 0 ? html.substring(body) : html;
        content = SCRIPT_OR_STYLE.matcher(content).replaceAll(" ");
        content = BLOCK_TAG_END.matcher(content).replaceAll("\n");
        content = ANY_TAG.matcher(content).replaceAll("");
        content = HtmlUtils.htmlUnescape(content);
        content = content
            .replace('\u00a0', ' ')
            .replaceAll("[ \\t]+", " ")
            .replaceAll(" ?\\n ?", "\n");
        content = BLANK_LINES.matcher(content).replaceAll("\n\n").trim();
        if (content.length() > MAX_SUMMARY) {
            content = content.substring(0, MAX_SUMMARY) + "…";
        }
        return content.isBlank() ? null : content;
    }

    private String fetch(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header(
                "User-Agent",
                "legis-entropy-graphs-service/1.0"
            )
            .GET()
            .build();
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<byte[]> resp = httpClient.send(
                    req,
                    HttpResponse.BodyHandlers.ofByteArray()
                );
                if (resp.statusCode() != 200) {
                    throw new IllegalStateException(
                        "adilet.zan.kz returned HTTP "
                            + resp.statusCode()
                            + " for "
                            + url
                    );
                }
                return new String(
                    resp.body(),
                    java.nio.charset.StandardCharsets.UTF_8
                );
            } catch (java.io.IOException e) {
                last = new IllegalStateException(
                    "Failed to fetch " + url + ": " + e.getMessage(),
                    e
                );
                log.warn(
                    "Fetch attempt {}/3 failed for {}: {}",
                    attempt,
                    url,
                    e.getMessage()
                );
                Thread.sleep(2000L * attempt);
            }
        }
        throw last;
    }
}
