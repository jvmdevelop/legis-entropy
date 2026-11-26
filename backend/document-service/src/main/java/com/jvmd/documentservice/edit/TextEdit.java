package com.jvmd.documentservice.edit;

public record TextEdit(String original, String replacement, int occurrence) {
    public TextEdit {
        if (original == null || original.isEmpty()) throw new IllegalArgumentException("original is required");
        if (replacement == null) replacement = "";
        if (occurrence < 0) occurrence = 0;
    }
}
