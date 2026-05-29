package com.jvmd.dms.law.model;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Law {

    private Long id;

    private String title;

    private String content;

    private LawStatus status;

    private LawCategory category;

    private String country;

    private String sourceUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String documentNumber;
    private String documentType;
    private String adoptedDate;
    private String issuedBy;
    private String invalidDate;

    public Map<String, Object> getMetaData() {
        Map<String, Object> meta = new HashMap<>();
        meta.put("title", title != null ? title : "");
        meta.put("status", status != null ? status.name() : "VALID");
        meta.put("category", category != null ? category.name() : "UNKNOWN");
        meta.put("country", country != null ? country : "UNKNOWN");
        meta.put("sourceUrl", sourceUrl != null ? sourceUrl : "");
        if (documentNumber != null) meta.put("documentNumber", documentNumber);
        if (documentType != null) meta.put("documentType", documentType);
        if (adoptedDate != null) meta.put("adoptedDate", adoptedDate);
        if (issuedBy != null) meta.put("issuedBy", issuedBy);
        if (invalidDate != null) meta.put("invalidDate", invalidDate);
        return meta;
    }

    public String getVectorStoreCollection() {
        return (
            (country != null ? country.toLowerCase() : "unknown") +
            "_" +
            (category != null ? category.name().toLowerCase() : "unknown")
        );
    }
}
