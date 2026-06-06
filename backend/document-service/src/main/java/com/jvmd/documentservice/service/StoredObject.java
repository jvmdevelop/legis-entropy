package com.jvmd.documentservice.service;

public record StoredObject(String fileName, String contentType, byte[] content) {
}
