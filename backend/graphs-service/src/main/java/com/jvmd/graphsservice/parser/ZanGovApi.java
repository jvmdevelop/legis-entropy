package com.jvmd.graphsservice.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ZanGovApi {

    private final ObjectMapper objectMapper;

    @Value("${parser.kz.base-url:https://zan.gov.kz}")
    private String baseUrl;

    private HttpClient httpClient;

    private final Map<String, String> actTypeNames = new ConcurrentHashMap<>();

    private final Map<String, String> legalValidityNames =
        new ConcurrentHashMap<>();

    private final Map<String, String> stateAgencyNames =
        new ConcurrentHashMap<>();

    private final AtomicBoolean dictionariesLoaded = new AtomicBoolean(false);

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
            .sslContext(trustAllContext())
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        log.info("ZanGovApi initialized with base URL: {}", baseUrl);
    }

    public JsonNode fetchDocument(String code, String lang) throws Exception {
        String url = baseUrl + "/api/documents/" + code + "/" + lang;
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Accept", "application/json")
            .header("User-Agent", "legis-entropy-graphs-service/1.0")
            .GET()
            .build();
        HttpResponse<byte[]> resp = httpClient.send(
            req,
            HttpResponse.BodyHandlers.ofByteArray()
        );
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(
                "zan.gov.kz returned HTTP " + resp.statusCode() + " for " + url
            );
        }
        return objectMapper.readTree(resp.body());
    }

    public JsonNode search(Map<String, Object> query) throws Exception {
        String url = baseUrl + "/api/documents/search";
        byte[] body = objectMapper.writeValueAsBytes(query);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "legis-entropy-graphs-service/1.0")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        HttpResponse<byte[]> resp = httpClient.send(
            req,
            HttpResponse.BodyHandlers.ofByteArray()
        );
        if (resp.statusCode() != 200) {
            throw new IllegalStateException(
                "zan.gov.kz search HTTP " + resp.statusCode()
            );
        }
        return objectMapper.readTree(resp.body());
    }

    public String actTypeName(String code) {
        if (code == null) return null;
        ensureDictionaries();
        return actTypeNames.get(code);
    }

    public String legalValidityName(String code) {
        if (code == null) return null;
        ensureDictionaries();
        return legalValidityNames.get(code);
    }

    public String stateAgencyName(String code) {
        if (code == null) return null;
        ensureDictionaries();
        return stateAgencyNames.get(code);
    }

    private synchronized void ensureDictionaries() {
        if (dictionariesLoaded.get()) return;
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("actTypes", 0);
            req.put("legalValidities", 0);
            req.put("stateAgencies", 0);
            String url = baseUrl + "/api/dictionaries";
            HttpRequest httpReq = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofByteArray(
                        objectMapper.writeValueAsBytes(req)
                    )
                )
                .build();
            HttpResponse<byte[]> resp = httpClient.send(
                httpReq,
                HttpResponse.BodyHandlers.ofByteArray()
            );
            if (resp.statusCode() != 200) {
                log.warn(
                    "zan.gov.kz dictionaries HTTP {} — falling back to raw codes",
                    resp.statusCode()
                );
                dictionariesLoaded.set(true);
                return;
            }
            JsonNode root = objectMapper.readTree(resp.body());
            for (JsonNode dict : root) {
                String name = dict.path("dict").asText("");
                Map<String, String> target = switch (
                    name.toLowerCase(Locale.ROOT)
                ) {
                    case "acttypes" -> actTypeNames;
                    case "legalvalidities" -> legalValidityNames;
                    case "stateagencies" -> stateAgencyNames;
                    default -> null;
                };
                if (target == null) continue;
                for (JsonNode item : dict.path("list")) {
                    String code = item.path("code").asText(null);
                    String rus = item.path("rus").asText(null);
                    if (code != null && rus != null) target.put(
                        code,
                        rus.trim()
                    );
                }
            }
            log.info(
                "Loaded zan.gov.kz dictionaries: actTypes={} legalValidities={} stateAgencies={}",
                actTypeNames.size(),
                legalValidityNames.size(),
                stateAgencyNames.size()
            );
            dictionariesLoaded.set(true);
        } catch (Exception e) {
            log.warn(
                "Failed to load zan.gov.kz dictionaries: {}",
                e.getMessage()
            );
            dictionariesLoaded.set(true);
        }
    }

    private static SSLContext trustAllContext() {
        try {
            TrustManager[] trustAll = {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(
                        X509Certificate[] chain,
                        String authType
                    ) {}

                    @Override
                    public void checkServerTrusted(
                        X509Certificate[] chain,
                        String authType
                    ) {}

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                },
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException(
                "Cannot build trust-all SSLContext",
                e
            );
        }
    }
}
