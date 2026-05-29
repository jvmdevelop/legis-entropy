package com.jvmd.dms.service;

import com.jvmd.dms.document.reader.DocumentFormatReaderFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DocumentReaderService {

    private final DocumentFormatReaderFactory readerFactory;

    public List<Document> read(MultipartFile file) {
        try {
            return readerFactory.read(file);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read uploaded file.", e);
        }
    }

    public List<Document> getDocsFromPdf(MultipartFile file) {
        return read(file);
    }
}
