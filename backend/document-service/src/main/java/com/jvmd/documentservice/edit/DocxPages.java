package com.jvmd.documentservice.edit;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class DocxPages {

    public record Page(int page, int start, int end) {}

    private DocxPages() {}

    public static List<Page> fromDocx(byte[] docx) throws Exception {
        byte[] xml = DocxEditor.readEntry(docx, DocxEditor.DOCUMENT_XML);
        if (xml == null) throw new IllegalArgumentException("DOCX has no word/document.xml");
        return fromXml(new String(xml, StandardCharsets.UTF_8));
    }

    public static List<Page> fromXml(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        org.w3c.dom.Document dom = f.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        StringBuilder text = new StringBuilder();
        List<Integer> breaks = new ArrayList<>();
        NodeList paragraphs = dom.getElementsByTagNameNS("*", "p");
        for (int i = 0; i < paragraphs.getLength(); i++) {
            walk(paragraphs.item(i).getChildNodes(), text, breaks);
            text.append('\n');
        }
        return split(text.toString(), breaks);
    }

    private static void walk(NodeList nodes, StringBuilder text, List<Integer> breaks) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String name = node.getLocalName();
            if ("t".equals(name)) {
                text.append(node.getTextContent());
            } else if ("tab".equals(name)) {
                text.append('\t');
            } else if ("br".equals(name)) {
                if (node instanceof Element e && "page".equals(attr(e, "type"))) breaks.add(text.length());
                text.append('\n');
            } else if ("lastRenderedPageBreak".equals(name)) {
                breaks.add(text.length());
            }
            walk(node.getChildNodes(), text, breaks);
        }
    }

    private static String attr(Element e, String local) {
        String v = e.getAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", local);
        return v == null || v.isEmpty() ? e.getAttribute("w:" + local) : v;
    }

    static List<Page> split(String raw, List<Integer> breaks) {
        int lead = 0;
        while (lead < raw.length() && Character.isWhitespace(raw.charAt(lead))) lead++;
        int length = raw.strip().length();
        List<Page> pages = new ArrayList<>();
        int start = 0;
        for (int b : breaks.stream().distinct().sorted().toList()) {
            int pos = Math.max(0, Math.min(length, b - lead));
            if (pos > start) {
                pages.add(new Page(pages.size() + 1, start, pos));
                start = pos;
            }
        }
        if (start < length || pages.isEmpty()) pages.add(new Page(pages.size() + 1, start, length));
        return pages;
    }

    public static List<Page> byLengths(List<String> pageTexts) {
        List<Page> pages = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < pageTexts.size(); i++) {
            String t = pageTexts.get(i);
            if (t == null || t.isBlank()) continue;
            if (offset > 0) offset += 2;
            pages.add(new Page(i + 1, offset, offset + t.length()));
            offset += t.length();
        }
        return pages;
    }

    public static List<Page> byFormFeed(String text) {
        List<Page> pages = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || text.charAt(i) == '\f') {
                if (i > start || pages.isEmpty()) pages.add(new Page(pages.size() + 1, start, i));
                start = i + 1;
            }
        }
        return pages;
    }
}
