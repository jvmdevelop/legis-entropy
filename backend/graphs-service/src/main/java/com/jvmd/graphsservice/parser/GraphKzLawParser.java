package com.jvmd.graphsservice.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.jvmd.graphsservice.model.Law;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class GraphKzLawParser extends GraphLawParser {

    private final CitationCodeBuilder citationCodeBuilder;
    private final ZanGovApi zanGovApi;
    private final ZanGovJsonExtractor jsonExtractor;

    public record ParsedKzLaw(
        Law law,
        LawStructure.ExtractionResult structure
    ) {}

    private static String mapStatus(String zanStatus) {
        if (zanStatus == null) return "ACTIVE";
        return switch (zanStatus.toLowerCase(Locale.ROOT)) {
            case "yts", "vexp", "bak" -> "EXPIRED";
            case "del", "err" -> "INVALID";
            default -> "ACTIVE";
        };
    }

    private static final Pattern ZAN_SPA_DOC = Pattern.compile(
        "zan\\.gov\\.kz/client/?#!?/doc/(\\d+)/(rus|kaz|eng)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ZAN_API_DOC = Pattern.compile(
        "zan\\.gov\\.kz/api/documents/(\\d+)/(rus|kaz|eng)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ADILET_DOC = Pattern.compile(
        "adilet\\.zan\\.kz/(rus|kaz)/docs/([A-Z]\\d{8,12})",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NUMERIC_CODE = Pattern.compile("^\\d{1,8}$");

    private static final DateTimeFormatter ISO_DATE =
        DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public Law parse(String url) {
        return parseFull(url).law();
    }

    public ParsedKzLaw parseFull(String url) {
        try {
            CodeAndLang ref = resolveDocumentRef(url);
            if (ref == null) {
                throw new IllegalArgumentException(
                    "Cannot resolve zan.gov.kz code from URL: " + url
                );
            }

            JsonNode doc = zanGovApi.fetchDocument(ref.code, ref.lang);
            JsonNode version = doc.path("version");
            JsonNode metadata = version.path("metadata");

            String canonicalUrl =
                "https://zan.gov.kz/client/#!/doc/" + ref.code + "/" + ref.lang;

            Law law = new Law();
            law.setCountry("RK");
            law.setLanguage(ref.lang);
            law.setSourceUri(canonicalUrl);

            JsonNode titleNode = metadata.path("title");
            String title = titleNode.path(ref.lang).asText(null);
            if (title == null || title.isBlank()) title = titleNode
                .path("rus")
                .asText(null);
            if (title == null || title.isBlank()) title = "Untitled Document";
            law.setTitle(title.trim());

            law.setStatus(mapStatus(metadata.path("status").asText(null)));

            law.setInternalNumber(
                blankToNull(metadata.path("stateAgencyDocNumber").asText(null))
            );
            law.setRegistryNumber(
                blankToNull(metadata.path("registryNumber").asText(null))
            );
            law.setAdoptionDate(
                parseIsoDate(
                    metadata.path("stateAgencyApprovalDate").asText(null)
                )
            );
            law.setLastAmendmentDate(
                parseIsoDate(version.path("versionDate").asText(null))
            );

            String actTypeCode = firstArrayString(metadata.path("actTypes"));
            String actTypeName = zanGovApi.actTypeName(actTypeCode);
            law.setType(actTypeName != null ? actTypeName : actTypeCode);
            law.setFormOfAct(actTypeName != null ? actTypeName : actTypeCode);

            String legalValidityCode = metadata
                .path("legalValidity")
                .asText(null);
            String legalValidityName = zanGovApi.legalValidityName(
                legalValidityCode
            );
            if (legalValidityName != null) law.setLegalForce(legalValidityName);

            String approvalPlace = metadata.path("approvalPlace").asText(null);
            if (approvalPlace != null) {
                String name = zanGovApi.stateAgencyName(approvalPlace);
                law.setPlaceOfAdoption(name != null ? name : approvalPlace);
            }
            JsonNode approvingAgencies = metadata.path(
                "approvingStateAgencies"
            );
            if (approvingAgencies.isArray() && !approvingAgencies.isEmpty()) {
                String agencyCode = approvingAgencies.get(0).asText();
                String name = zanGovApi.stateAgencyName(agencyCode);
                law.setIssuingBody(name != null ? name : agencyCode);
            }
            String developingAgency = metadata
                .path("developingStateAgency")
                .asText(null);
            if (developingAgency != null && !developingAgency.isBlank()) {
                String name = zanGovApi.stateAgencyName(developingAgency);
                law.setDeveloperBody(name != null ? name : developingAgency);
            }

            String requisites = metadata
                .path("requisites")
                .path(ref.lang)
                .asText(null);
            if (requisites == null || requisites.isBlank()) {
                requisites = metadata
                    .path("requisites")
                    .path("rus")
                    .asText(null);
            }
            if (
                requisites != null &&
                requisites.toLowerCase(Locale.ROOT).contains("утратил")
            ) {
                law.setStatus("EXPIRED");
            }
            JsonNode publication = metadata.path("publication");
            if (publication.isArray() && !publication.isEmpty()) {
                JsonNode p = publication.get(0);
                String date = p.path("date").asText("");
                String issue = p.path("issue").asText("");
                law.setOfficialPublication(
                    (
                        "Опубликован" +
                        (date.isBlank() ? "" : " " + date) +
                        (issue.isBlank() ? "" : ", вып. " + issue)
                    ).trim()
                );
            }

            String citation = citationCodeBuilder.build(
                law.getTitle(),
                law.getFormOfAct() != null ? law.getFormOfAct() : law.getType(),
                law.getInternalNumber(),
                law.getCountry()
            );
            law.setCitationCode(citation);
            law.setCode(
                firstNonBlank(
                    law.getCitationCode(),
                    law.getInternalNumber(),
                    law.getRegistryNumber()
                )
            );

            law.setSummary(buildSummary(doc.path("content")));

            LawStructure.ExtractionResult structure;
            if (law.getCode() != null) {
                structure = jsonExtractor.extract(
                    doc,
                    law.getCode(),
                    law.getCountry(),
                    canonicalUrl
                );
            } else {
                structure = new LawStructure.ExtractionResult(
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of()
                );
            }
            return new ParsedKzLaw(law, structure);
        } catch (Exception e) {
            log.warn(
                "Failed to parse zan.gov.kz law {}: {}",
                url,
                e.getMessage()
            );
            Law errorLaw = new Law(
                "Parse Error: " + url,
                null,
                "RK",
                "PARSE_ERROR",
                "Failed to parse document: " + e.getMessage()
            );
            errorLaw.setStatus("INVALID");
            return new ParsedKzLaw(
                errorLaw,
                new LawStructure.ExtractionResult(
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of()
                )
            );
        }
    }

    public void enrichMetadataInPlace(String docUrl, Law law) {
        try {
            CodeAndLang ref = resolveDocumentRef(docUrl);
            if (ref == null) return;
            JsonNode doc = zanGovApi.fetchDocument(ref.code, ref.lang);
            JsonNode metadata = doc.path("version").path("metadata");
            if (law.getInternalNumber() == null) {
                law.setInternalNumber(
                    blankToNull(
                        metadata.path("stateAgencyDocNumber").asText(null)
                    )
                );
            }
            if (law.getRegistryNumber() == null) {
                law.setRegistryNumber(
                    blankToNull(metadata.path("registryNumber").asText(null))
                );
            }
            if (law.getAdoptionDate() == null) {
                law.setAdoptionDate(
                    parseIsoDate(
                        metadata.path("stateAgencyApprovalDate").asText(null)
                    )
                );
            }
            if (law.getFormOfAct() == null) {
                String actTypeCode = firstArrayString(
                    metadata.path("actTypes")
                );
                String name = zanGovApi.actTypeName(actTypeCode);
                law.setFormOfAct(name != null ? name : actTypeCode);
                if (law.getType() == null) law.setType(law.getFormOfAct());
            }
            String citation = citationCodeBuilder.build(
                law.getTitle(),
                law.getFormOfAct() != null ? law.getFormOfAct() : law.getType(),
                law.getInternalNumber(),
                law.getCountry()
            );
            if (citation != null) law.setCitationCode(citation);
            String preferred = firstNonBlank(
                law.getCitationCode(),
                law.getInternalNumber(),
                law.getRegistryNumber()
            );
            if (preferred != null) law.setCode(preferred);
        } catch (Exception e) {
            log.debug(
                "Could not enrich {} from zan.gov.kz: {}",
                docUrl,
                e.getMessage()
            );
        }
    }

    private record CodeAndLang(String code, String lang) {}

    private CodeAndLang resolveDocumentRef(String url) {
        if (url == null || url.isBlank()) return null;
        if (NUMERIC_CODE.matcher(url.trim()).matches()) {
            return new CodeAndLang(url.trim(), "rus");
        }
        Matcher m = ZAN_SPA_DOC.matcher(url);
        if (m.find()) return new CodeAndLang(
            m.group(1),
            m.group(2).toLowerCase()
        );
        m = ZAN_API_DOC.matcher(url);
        if (m.find()) return new CodeAndLang(
            m.group(1),
            m.group(2).toLowerCase()
        );
        m = ADILET_DOC.matcher(url);
        if (m.find()) {
            String lang = m.group(1).toLowerCase();
            String ngr = m.group(2).toUpperCase();
            String resolved = resolveByNgr(ngr, lang);
            if (resolved != null) return new CodeAndLang(resolved, lang);
            log.warn("Could not resolve adilet NGR {} on zan.gov.kz", ngr);
            return null;
        }
        return null;
    }

    private String resolveByNgr(String ngr, String lang) {
        try {
            JsonNode resp = zanGovApi.search(
                java.util.Map.of(
                    "text",
                    ngr,
                    "page",
                    1,
                    "limit",
                    5,
                    "lang",
                    lang
                )
            );
            for (JsonNode item : resp.path("list")) {
                String id = item.path("id").asText(null);
                if (id != null) return id;
            }
        } catch (Exception e) {
            log.debug("NGR lookup failed for {}: {}", ngr, e.getMessage());
        }
        return null;
    }

    private static String buildSummary(JsonNode content) {
        if (!content.isArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode node : content) {
            String type = node.path("type").asText("");
            if (
                !"text".equals(type) &&
                !"heading".equals(type) &&
                !"title".equals(type) &&
                !"subheading".equals(type) &&
                !"pre".equals(type)
            ) continue;
            String t = node.path("text").asText("").trim();
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(t);
            if (sb.length() > 200_000) {
                sb.append("…");
                break;
            }
        }
        return sb.toString();
    }

    private static LocalDate parseIsoDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s, ISO_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstArrayString(JsonNode arr) {
        if (!arr.isArray() || arr.isEmpty()) return null;
        String s = arr.get(0).asText(null);
        return s == null || s.isBlank() ? null : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() || "НЕТ".equalsIgnoreCase(s) ? null : s;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (
                v != null && !v.isBlank() && !"НЕТ".equalsIgnoreCase(v)
            ) return v;
        }
        return null;
    }
}
