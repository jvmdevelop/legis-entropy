package com.jvmd.graphsservice.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("Article")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Article {

    @Id
    @GeneratedValue
    private Long id;

    private String lawCode;
    private String country;

    private String number;
    private String title;
    private String body;

    private String anchor;
    private String partTitle;
    private String sectionNumber;
    private String sectionTitle;
    private String chapterNumber;
    private String chapterTitle;

    private LocalDate lastAmendmentDate;

    private LocalDate validFrom;
    private LocalDate validUntil;

    private LocalDateTime recordedAt;
    private LocalDateTime supersededAt;

    private ProvenanceSource source;
    private String extractedBy;
    private LocalDateTime extractedAt;
    private Double confidence;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private String sourceUri;

    @Relationship(
        type = "REFERENCES_ARTICLE",
        direction = Relationship.Direction.OUTGOING
    )
    private List<Article> references = new ArrayList<>();

    @Relationship(
        type = "AMENDED_BY",
        direction = Relationship.Direction.OUTGOING
    )
    private List<Law> amendedBy = new ArrayList<>();

    public Article(
        String lawCode,
        String country,
        String number,
        String title,
        String body
    ) {
        this.lawCode = lawCode;
        this.country = country;
        this.number = number;
        this.title = title;
        this.body = body;
        this.recordedAt = LocalDateTime.now();
    }

    public void applyProvenance(Provenance p) {
        if (p == null) return;
        this.source = p.getSource();
        this.extractedBy = p.getExtractedBy();
        this.extractedAt = p.getExtractedAt();
        this.confidence = p.getConfidence();
        this.verifiedBy = p.getVerifiedBy();
        this.verifiedAt = p.getVerifiedAt();
        this.sourceUri = p.getSourceUri();
    }
}
