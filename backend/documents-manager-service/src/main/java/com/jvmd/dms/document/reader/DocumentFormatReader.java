package com.jvmd.dms.document.reader;

import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface DocumentFormatReader {
    List<Document> read(MultipartFile file) throws IOException;
    boolean supports(String filename, String contentType);
}
