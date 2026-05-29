package com.jvmd.dms.law.graph;

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
@Node("KzArticle")
public class KzArticleGraphNode {

    @Id
    @GeneratedValue
    private Long id;

    @Property("uuid")
    private UUID uuid;

    @Property
    private Integer articleNumber;

    @Property
    private String title;

    @Property
    private String lawCode;

    @Property
    private String lawTitle;

    @Property
    private Boolean isActive;

    @Property
    private Integer version;

    public String getFullReference() {
        return String.format("Статья %d %s", articleNumber, lawTitle);
    }
}
