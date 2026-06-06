package com.jvmd.lawservice.model.kz;

public enum KzLegislativeDocumentType {
    CONSTITUTION("Конституция"),
    CODE("Кодекс"),
    LAW("Закон"),
    PRESIDENTIAL_DECREE("Указ Президента"),
    GOVERNMENT_RESOLUTION("Постановление Правительства"),
    MINISTERIAL_ORDER("Приказ Министерства"),
    AMENDMENT("Изменение");

    private final String displayName;

    KzLegislativeDocumentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
