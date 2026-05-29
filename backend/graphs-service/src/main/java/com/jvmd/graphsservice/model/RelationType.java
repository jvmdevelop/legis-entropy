package com.jvmd.graphsservice.model;

public enum RelationType {
    REFERENCES("ссылается на", "references"),
    AMENDS("изменяет", "amends"),
    DEFINES("определяет", "defines"),
    RELATED("связан с", "related"),
    SUPERSEDES("заменяет", "supersedes"),
    IMPLEMENTS("имплементирует", "implements");

    private final String label;
    private final String value;

    RelationType(String label, String value) {
        this.label = label;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }
}
