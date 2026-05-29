package com.jvmd.dms.law.model.kz;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("CODE")
public class Code extends KzLegislativeDocument {

    public Code() {
        this.setDocumentType(KzLegislativeDocumentType.CODE);
    }

    @Override
    public String getHierarchyLevel() {
        return "Level 1: Кодекс (" + this.getCode() + ")";
    }
}
