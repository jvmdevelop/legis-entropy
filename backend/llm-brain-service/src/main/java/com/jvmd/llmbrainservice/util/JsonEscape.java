package com.jvmd.llmbrainservice.util;

public final class JsonEscape {

    private JsonEscape() {}

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
