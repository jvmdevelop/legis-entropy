package com.jvmd.documentservice.document.reader;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class DocumentFormatReaderFactory {

    private final List<DocumentFormatReader> readers;

    public List<Document> read(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);

        return readers.stream()
                .filter(r -> r.supports(filename, contentType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file type. Upload PDF, DOCX or TXT files."))
                .read(file);
    }
}
