package com.jvmd.dms.document.reader;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class TextDocumentFormatReader implements DocumentFormatReader {

    @Override
    public boolean supports(String filename, String contentType) {
        return filename.endsWith(".txt") || contentType.startsWith("text/plain");
    }

    @Override
    public List<Document> read(MultipartFile file) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Uploaded text file is empty.");
        }
        return List.of(new Document(text));
    }
}
