package com.jvmd.dms.service;

public record DocumentIngestionRequest(String documentId, String userId, String fileName) {
}
