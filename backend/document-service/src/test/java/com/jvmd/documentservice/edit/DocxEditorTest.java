package com.jvmd.documentservice.edit;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class DocxEditorTest {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private static byte[] contract() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"" + W + "\"><w:body>"
                + "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t>3.1. Согласно статьёй 4</w:t></w:r><w:r><w:t>9 ТК РК испытание.</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t>6.1. Отпуск</w:t></w:r><w:r><w:tab/></w:r><w:r><w:t>21 день.</w:t></w:r></w:p>"
                + "<w:p><w:r><w:t xml:space=\"preserve\">9.2. Предупредив по статьёй 49 ТК РК.</w:t></w:r></w:p>"
                + "</w:body></w:document>";
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(b)) {
            z.putNextEntry(new ZipEntry("[Content_Types].xml"));
            z.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("word/document.xml"));
            z.write(xml.getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("word/styles.xml"));
            z.write("<styles>keep</styles>".getBytes(StandardCharsets.UTF_8));
        }
        return b.toByteArray();
    }

    private static String xml(byte[] docx, String entry) throws Exception {
        return new String(DocxEditor.readEntry(docx, entry), StandardCharsets.UTF_8);
    }

    @Test
    void editsAcrossRunsByOccurrenceAndKeepsOtherParts() throws Exception {
        EditResult r = DocxEditor.apply(contract(), List.of(
                new TextEdit("статьёй 49", "статьёй 36", 0),
                new TextEdit("статьёй 49", "статьёй 56", 1),
                new TextEdit("Отпуск\t21", "Отпуск 24", 0),
                new TextEdit("нет такого текста", "x", 0)));
        assertEquals(List.of(0, 1), r.applied());
        assertEquals(List.of(2, 3), r.skipped().stream().map(EditResult.Skipped::index).toList());
        String doc = xml(r.content(), "word/document.xml");
        assertTrue(doc.contains("3.1. Согласно статьёй 36"), doc);
        assertTrue(doc.contains("> ТК РК испытание.<"), doc);
        assertTrue(doc.contains("по статьёй 56 ТК РК"), doc);
        assertTrue(doc.contains("<w:b/>"), "run formatting kept");
        assertEquals("<styles>keep</styles>", xml(r.content(), "word/styles.xml"));
    }

    @Test
    void nothingAppliedReturnsOriginalBytes() throws Exception {
        byte[] src = contract();
        EditResult r = DocxEditor.apply(src, List.of(new TextEdit("абв", "где", 0)));
        assertTrue(r.applied().isEmpty());
        assertSame(src, r.content());
    }

    @Test
    void plainTextEditsResolveBeforeApplying() {
        String text = "a X b X c";
        List<EditResult.Skipped> skipped = new java.util.ArrayList<>();
        var spans = PlainTextEdits.resolve(text, List.of(new TextEdit("X", "YY", 1), new TextEdit("X", "Z", 0),
                new TextEdit("X b", "q", 0)), skipped);
        assertEquals("a Z b YY c", PlainTextEdits.apply(text, spans));
        assertEquals(1, skipped.size());
    }

    @Test
    void docxWriterProducesReadableDocument() throws Exception {
        byte[] docx = DocxWriter.fromText("1.1. Строка <один> & \"два\"\n1.2. Вторая");
        String doc = xml(docx, "word/document.xml");
        assertTrue(doc.contains("1.1. Строка &lt;один&gt; &amp; &quot;два&quot;"));
        assertEquals(2, doc.split("<w:p>").length - 1);
        assertNotNull(DocxEditor.readEntry(docx, "[Content_Types].xml"));
    }
}
