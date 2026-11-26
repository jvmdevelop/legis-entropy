package com.jvmd.documentservice.edit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocxPagesTest {

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    @Test
    void splitsOnRenderedAndExplicitPageBreaks() throws Exception {
        String xml = "<w:document xmlns:w=\"" + W + "\"><w:body>"
                + "<w:p><w:r><w:t>1.1. Первая страница</w:t></w:r></w:p>"
                + "<w:p><w:r><w:lastRenderedPageBreak/><w:t>2.1. Вторая</w:t></w:r></w:p>"
                + "<w:p><w:r><w:br w:type=\"page\"/><w:t>3.1. Третья</w:t></w:r></w:p>"
                + "</w:body></w:document>";
        List<DocxPages.Page> pages = DocxPages.fromXml(xml);
        String text = "1.1. Первая страница\n2.1. Вторая\n\n3.1. Третья";
        assertEquals(3, pages.size());
        assertEquals("1.1. Первая страница\n", text.substring(pages.get(0).start(), pages.get(0).end()));
        assertEquals("2.1. Вторая\n", text.substring(pages.get(1).start(), pages.get(1).end()));
        assertEquals(text.length(), pages.get(2).end());
    }

    @Test
    void pdfOffsetsMatchJoinedText() {
        List<String> texts = List.of("стр 1", "  ", "стр 3");
        List<DocxPages.Page> pages = DocxPages.byLengths(texts);
        String joined = "стр 1\n\nстр 3";
        assertEquals(List.of(1, 3), pages.stream().map(DocxPages.Page::page).toList());
        assertEquals("стр 3", joined.substring(pages.get(1).start(), pages.get(1).end()));
    }

    @Test
    void formFeedAndSinglePage() {
        assertEquals(2, DocxPages.byFormFeed("a\fb").size());
        assertEquals(1, DocxPages.byFormFeed("без разрывов").size());
    }
}
