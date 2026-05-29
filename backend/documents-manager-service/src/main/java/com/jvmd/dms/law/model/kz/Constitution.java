package com.jvmd.dms.law.model.kz;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("CONSTITUTION")
public class Constitution extends KzLegislativeDocument {

    public Constitution() {
        this.setDocumentType(KzLegislativeDocumentType.CONSTITUTION);
    }

    @Override
    public String getHierarchyLevel() {
        return "Level 0: Конституция";
    }
}
