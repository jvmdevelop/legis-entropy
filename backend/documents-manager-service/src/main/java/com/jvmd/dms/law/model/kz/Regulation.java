package com.jvmd.dms.law.model.kz;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@NoArgsConstructor
@DiscriminatorValue("REGULATION")
public class Regulation extends KzLegislativeDocument {

    @Enumerated(EnumType.STRING)
    @Column(name = "regulation_type")
    private RegulationType regulationType;

    public Regulation(RegulationType regulationType) {
        this.regulationType = regulationType;
        this.setDocumentType(KzLegislativeDocumentType.PRESIDENTIAL_DECREE);
    }

    @Override
    public String getHierarchyLevel() {
        return "Level 3: Подзаконный акт (" + (regulationType != null ? regulationType.getDisplayName() : "Unknown") + ")";
    }

    public enum RegulationType {
        PRESIDENTIAL_DECREE("Указ Президента"),
        GOVERNMENT_RESOLUTION("Постановление Правительства"),
        MINISTERIAL_ORDER("Приказ Министерства"),
        INSTRUCTION("Инструкция");

        private final String displayName;

        RegulationType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
