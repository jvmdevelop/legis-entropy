package com.jvmd.documentservice.service;

public record DocumentIngestionRequest(String documentId, String userId, String fileName) {
}
