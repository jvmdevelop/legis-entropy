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

@Node("Law")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Law {

    @Id
    @GeneratedValue
    private Long id;

    private String title;
    private String code;
    private String country;
    private String type;
    private LocalDate adoptionDate;
    private LocalDate effectiveDate;
    private String status;
    private String summary;

    private String citationCode;

    private String registryNumber;

    private String internalNumber;

    private String justiceRegistrationNumber;

    private String legalForce;

    private String formOfAct;

    private String subjectArea;

    private String databaseSection;

    private String issuingBody;

    private String developerBody;

    private String regionOfEffect;

    private String placeOfAdoption;

    private LocalDate lastAmendmentDate;

    private LocalDate justiceRegistrationDate;

    private String officialPublication;

    private String language;

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
        type = "HAS_ARTICLE",
        direction = Relationship.Direction.OUTGOING
    )
    private List<Article> articles = new ArrayList<>();

    @Relationship(
        type = "REFERENCES",
        direction = Relationship.Direction.OUTGOING
    )
    private List<Law> references = new ArrayList<>();

    @Relationship(type = "AMENDS", direction = Relationship.Direction.OUTGOING)
    private List<Law> amends = new ArrayList<>();

    @Relationship(type = "DEFINES", direction = Relationship.Direction.OUTGOING)
    private List<Law> defines = new ArrayList<>();

    @Relationship(type = "RELATED", direction = Relationship.Direction.OUTGOING)
    private List<Law> related = new ArrayList<>();

    @Relationship(
        type = "SUPERSEDES",
        direction = Relationship.Direction.OUTGOING
    )
    private List<Law> supersedes = new ArrayList<>();

    @Relationship(
        type = "IMPLEMENTS",
        direction = Relationship.Direction.OUTGOING
    )
    private List<Law> implementation = new ArrayList<>();

    public Law(
        String title,
        String code,
        String country,
        String type,
        String summary
    ) {
        this.title = title;
        this.code = code;
        this.country = country;
        this.type = type;
        this.summary = summary;
        this.status = "ACTIVE";
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
