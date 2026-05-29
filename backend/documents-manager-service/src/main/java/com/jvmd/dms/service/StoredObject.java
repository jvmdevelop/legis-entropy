package com.jvmd.dms.service;

public record StoredObject(String fileName, String contentType, byte[] content) {
}
