package com.jvmd.dms.law.parser.impl.impl;

import com.jvmd.dms.law.model.Law;
import com.jvmd.dms.law.model.LawCategory;
import com.jvmd.dms.law.model.LawStatus;
import com.jvmd.dms.law.parser.impl.LawParser;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@Slf4j
public class KzLawParser extends LawParser {

    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{1,2})\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\\s+(\\d{4})"
    );
    private static final Pattern DATE_DMY_PATTERN = Pattern.compile(
        "(\\d{2}\\.\\d{2}\\.\\d{4})"
    );
    private static final Pattern DOC_NUMBER_PATTERN = Pattern.compile(
        "№\\s*([\\w\\-]+(?:\\s+ЗРК|\\s+ПРК|\\s+РК)?)"
    );

    private static final Map<String, String> MONTHS = new HashMap<>();

    static {
        MONTHS.put("января", "01");
        MONTHS.put("февраля", "02");
        MONTHS.put("марта", "03");
        MONTHS.put("апреля", "04");
        MONTHS.put("мая", "05");
        MONTHS.put("июня", "06");
        MONTHS.put("июля", "07");
        MONTHS.put("августа", "08");
        MONTHS.put("сентября", "09");
        MONTHS.put("октября", "10");
        MONTHS.put("ноября", "11");
        MONTHS.put("декабря", "12");
    }

    @Override
    public Law parse(String url) {
        try {
            Document doc = AdiletJsoup.connect(url).get();

            Law law = new Law();
            law.setCountry("KZ");
            law.setSourceUrl(url);

            Element titleEl = doc.selectFirst(".container_alpha.slogan h1, h1");
            law.setTitle(
                titleEl != null ? titleEl.text().trim() : "Untitled Document"
            );

            law.setStatus(determineStatus(doc, url));

            Element sloganBlock = doc.selectFirst(".container_alpha.slogan");
            if (sloganBlock != null) {
                Element refEl = sloganBlock.selectFirst("p");
                if (refEl != null) {
                    String refText = refEl.text().trim();
                    law.setDocumentType(extractDocumentType(refText));
                    law.setAdoptedDate(extractDate(refText));
                    law.setDocumentNumber(extractDocumentNumber(refText));
                }
            }

            enrichFromInfoPage(url, law);

            Element contentEl = doc.selectFirst(
                "div.container_gamma.text, div.container_gamma.text_new, .text"
            );
            if (contentEl != null) {
                contentEl
                    .select("script, style, .social-buttons, .aftertext")
                    .remove();
                law.setContent(contentEl.text().trim());
            } else {
                Element body = doc.selectFirst("body");
                if (body != null) {
                    body.select("script, style, nav, header, footer").remove();
                    law.setContent(body.text().trim());
                } else {
                    law.setContent("No content found");
                }
            }

            law.setCategory(LawCategory.fromTitle(law.getTitle()));

            LocalDateTime now = LocalDateTime.now();
            law.setCreatedAt(now);
            law.setUpdatedAt(now);

            return law;
        } catch (IOException e) {
            log.warn(
                "Failed to parse KZ law document {}: {}",
                url,
                e.getMessage()
            );
            Law errorLaw = new Law();
            errorLaw.setTitle("Parse Error: " + url);
            errorLaw.setContent("Failed to parse document: " + e.getMessage());
            errorLaw.setStatus(LawStatus.VALID);
            errorLaw.setCategory(LawCategory.UNKNOWN);
            errorLaw.setCountry("KZ");
            errorLaw.setSourceUrl(url);
            errorLaw.setCreatedAt(LocalDateTime.now());
            errorLaw.setUpdatedAt(LocalDateTime.now());
            return errorLaw;
        }
    }

    private LawStatus determineStatus(Document doc, String url) {
        Element contentDiv = doc.selectFirst("div.container_gamma.text");
        if (contentDiv != null) {
            if (
                contentDiv.hasClass("text_yts") ||
                contentDiv.hasClass("text_arc")
            ) {
                return LawStatus.EXPIRED;
            }
        }

        if (doc.selectFirst("span.status_yts, span.status_arc") != null) {
            return LawStatus.EXPIRED;
        }

        return LawStatus.VALID;
    }

    private void enrichFromInfoPage(String docUrl, Law law) {
        try {
            String infoUrl = docUrl.endsWith("/")
                ? docUrl + "info"
                : docUrl + "/info";
            Document infoDoc = AdiletJsoup.connect(infoUrl).get();

            Elements rows = infoDoc.select("table.params tr");
            for (Element row : rows) {
                Elements cells = row.select("td");
                if (cells.size() < 2) continue;
                String param = cells.get(0).text().trim();
                String value = cells.get(1).text().trim();
                if (value.isEmpty()) continue;

                if (
                    param.contains("Дата принятия") &&
                    law.getAdoptedDate() == null
                ) {
                    law.setAdoptedDate(value);
                } else if (
                    param.contains("Орган, принявший") &&
                    law.getIssuedBy() == null
                ) {
                    law.setIssuedBy(shortenIssuedBy(value));
                } else if (
                    param.contains("Юридическая сила") &&
                    law.getDocumentType() == null
                ) {
                    law.setDocumentType(value);
                } else if (
                    param.contains(
                        "Регистрационный номер НПА, присвоенный нормотворческим"
                    ) &&
                    law.getDocumentNumber() == null
                ) {
                    law.setDocumentNumber(value);
                }
            }

            if (
                law.getStatus() == LawStatus.EXPIRED &&
                law.getInvalidDate() == null
            ) {
                String infoText =
                    infoDoc.body() != null ? infoDoc.body().text() : "";
                Matcher m = DATE_DMY_PATTERN.matcher(infoText);
                int expiryIdx = infoText.toLowerCase().indexOf("утратил силу");
                if (expiryIdx >= 0) {
                    String around = infoText.substring(
                        Math.max(0, expiryIdx - 20),
                        Math.min(infoText.length(), expiryIdx + 80)
                    );
                    Matcher dm = DATE_DMY_PATTERN.matcher(around);
                    if (dm.find()) {
                        law.setInvalidDate(dm.group(1));
                    }
                } else {
                    while (m.find()) {
                        String d = m.group(1);
                        if (
                            law.getAdoptedDate() == null ||
                            !d.equals(law.getAdoptedDate())
                        ) {
                            law.setInvalidDate(d);
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.debug(
                "Could not fetch info page for {}: {}",
                docUrl,
                e.getMessage()
            );
        }
    }

    private String extractDocumentType(String refText) {
        String[] types = {
            "Кодекс",
            "Конституционный закон",
            "Закон",
            "Постановление",
            "Указ",
            "Приказ",
            "Распоряжение",
        };
        for (String type : types) {
            if (refText.startsWith(type)) return type;
        }
        return null;
    }

    private String extractDate(String refText) {
        Matcher m = DATE_PATTERN.matcher(refText);
        if (m.find()) {
            String day = String.format("%02d", Integer.parseInt(m.group(1)));
            String month = MONTHS.getOrDefault(m.group(2).toLowerCase(), "01");
            String year = m.group(3);
            return day + "." + month + "." + year;
        }
        return null;
    }

    private String extractDocumentNumber(String refText) {
        Matcher m = DOC_NUMBER_PATTERN.matcher(refText);
        return m.find() ? m.group(1).trim() : null;
    }

    private String shortenIssuedBy(String fullName) {
        int bracket = fullName.indexOf('(');
        return bracket > 0 ? fullName.substring(0, bracket).trim() : fullName;
    }
}
