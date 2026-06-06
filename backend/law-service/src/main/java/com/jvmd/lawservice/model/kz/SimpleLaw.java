package com.jvmd.lawservice.model.kz;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("LAW")
public class SimpleLaw extends KzLegislativeDocument {

    public SimpleLaw() {
        this.setDocumentType(KzLegislativeDocumentType.LAW);
    }

    @Override
    public String getHierarchyLevel() {
        return "Level 2: Закон";
    }
}
