package com.jvmd.dms.service;

import com.jvmd.dms.document.reader.DocxDocumentFormatReader;
import com.jvmd.dms.document.reader.DocumentFormatReaderFactory;
import com.jvmd.dms.document.reader.PdfDocumentFormatReader;
import com.jvmd.dms.document.reader.TextDocumentFormatReader;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentReaderServiceTest {

    private final DocumentReaderService service = new DocumentReaderService(
            new DocumentFormatReaderFactory(List.of(
                    new PdfDocumentFormatReader(),
                    new TextDocumentFormatReader(),
                    new DocxDocumentFormatReader()
            ))
    );

    @Test
    void readsTextFileAsSingleDocument() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.txt",
                "text/plain",
                "  Текст договора  ".getBytes(StandardCharsets.UTF_8)
        );

        List<Document> documents = service.read(file);

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getText()).isEqualTo("Текст договора");
    }

    @Test
    void rejectsEmptyTextFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.txt",
                "text/plain",
                "   ".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Uploaded text file is empty.");
    }

    @Test
    void readsDocxFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <w:document xmlns:w="http:
                          <w:body>
                            <w:p><w:r><w:t>Первый пункт</w:t></w:r></w:p>
                            <w:p><w:r><w:t>Второй пункт</w:t></w:r></w:p>
                          </w:body>
                        </w:document>
                        """)
        );

        List<Document> documents = service.read(file);

        assertThat(documents).hasSize(1);
        assertThat(documents.getFirst().getText()).contains("Первый пункт").contains("Второй пункт");
    }

    @Test
    void rejectsUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.rtf",
                "application/rtf",
                "content".getBytes(StandardCharsets.UTF_8)
        );

        assertThatThrownBy(() -> service.read(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported file type. Upload PDF, DOCX or TXT files.");
    }

    private byte[] docx(String documentXml) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(documentXml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }
}
