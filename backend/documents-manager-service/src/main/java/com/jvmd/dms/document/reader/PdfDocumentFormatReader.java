package com.jvmd.dms.document.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Component
public class PdfDocumentFormatReader implements DocumentFormatReader {

    @Override
    public boolean supports(String filename, String contentType) {
        return filename.endsWith(".pdf") || "application/pdf".equals(contentType);
    }

    @Override
    public List<Document> read(MultipartFile file) {
        PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(
                file.getResource(),
                PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build())
                        .withPagesPerDocument(1)
                        .build()
        );
        return pdfReader.read();
    }
}
