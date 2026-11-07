package com.jvmd.graphsservice.dto;

public record GraphSearchRequest(String query, String country) {

    private static final int MAX_QUERY_LENGTH = 1000;

    public String safeQuery() {
        if (query == null) return "";
        String q = query.strip();
        return q.length() > MAX_QUERY_LENGTH ? q.substring(0, MAX_QUERY_LENGTH) : q;
    }

    public String safeCountry() {
        return country == null || country.isBlank() ? "RK" : country;
    }
}
