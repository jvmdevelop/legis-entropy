package kz.legis.entropy.domain;

public record SearchRequest(String query, Integer topK) {
    public SearchRequest {
        if (topK == null) topK = 5;
    }
}
