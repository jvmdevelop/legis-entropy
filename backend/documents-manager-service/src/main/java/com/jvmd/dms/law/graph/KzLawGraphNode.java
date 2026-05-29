package com.jvmd.dms.law.graph;

import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Node("KzLaw")
public class KzLawGraphNode {

    @Id
    @GeneratedValue
    private Long id;

    @Property("uuid")
    private UUID uuid;

    @Property
    private String title;

    @Property
    private String code;

    @Property
    private String documentType;

    @Property
    private String documentNumber;

    @Property
    private LocalDate datePublished;

    @Property
    private String issuedBy;

    @Property
    private Boolean isActive;

    @Property
    private Integer version;

    @Property
    private String hierarchyLevel;

    @Property
    private Integer articleCount;

    public String getLabel() {
        if ("CONSTITUTION".equals(documentType)) {
            return "Конституция РК";
        } else if ("CODE".equals(documentType)) {
            return code != null ? code + " РК" : title;
        } else if ("LAW".equals(documentType)) {
            return "Закон РК: " + title;
        } else {
            return title;
        }
    }
}
