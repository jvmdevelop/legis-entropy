package com.jvmd.documentservice.edit;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class DocxEditor {
    static final String DOCUMENT_XML = "word/document.xml";

    private static final String XML_NS = "http://www.w3.org/XML/1998/namespace";

    private record Segment(Element t, int start, String text) {
        int end() { return start + text.length(); }
    }

    private DocxEditor() {}

    public static EditResult apply(byte[] docx, List<TextEdit> edits) throws Exception {
        byte[] xml = readEntry(docx, DOCUMENT_XML);
        if (xml == null) throw new IllegalArgumentException("DOCX has no word/document.xml");
        org.w3c.dom.Document dom = parse(xml);

        List<Segment> segments = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        NodeList paragraphs = dom.getElementsByTagNameNS("*", "p");
        for (int i = 0; i < paragraphs.getLength(); i++) {
            collect(paragraphs.item(i).getChildNodes(), segments, text);
            add(segments, text, null, "\n");
        }
        List<EditResult.Skipped> skipped = new ArrayList<>();
        List<PlainTextEdits.Span> spans = PlainTextEdits.resolve(text.toString(), edits, skipped);
        List<Integer> applied = new ArrayList<>();
        List<PlainTextEdits.Span> mapped = new ArrayList<>();
        for (PlainTextEdits.Span s : spans) {
            if (covered(segments, s).stream().allMatch(seg -> seg.t() != null)) mapped.add(s);
            else skipped.add(new EditResult.Skipped(s.index(),
                    "Фрагмент пересекает границу абзаца, табуляцию или разрыв строки."));
        }
        for (int k = mapped.size() - 1; k >= 0; k--) {
            replace(covered(segments, mapped.get(k)), mapped.get(k));
            applied.add(0, mapped.get(k).index());
        }
        applied.sort(Integer::compare);
        skipped.sort(java.util.Comparator.comparingInt(EditResult.Skipped::index));
        if (applied.isEmpty()) return new EditResult(docx, applied, skipped);
        return new EditResult(writeEntry(docx, DOCUMENT_XML, serialize(dom)), applied, skipped);
    }

    private static void collect(NodeList nodes, List<Segment> segments, StringBuilder text) {
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            String name = node.getLocalName();
            if ("t".equals(name)) add(segments, text, (Element) node, node.getTextContent());
            else if ("tab".equals(name)) add(segments, text, null, "\t");
            else if ("br".equals(name)) add(segments, text, null, "\n");
            collect(node.getChildNodes(), segments, text);
        }
    }

    private static void add(List<Segment> segments, StringBuilder text, Element t, String s) {
        if (s.isEmpty()) return;
        segments.add(new Segment(t, text.length(), s));
        text.append(s);
    }

    private static List<Segment> covered(List<Segment> segments, PlainTextEdits.Span s) {
        return segments.stream().filter(seg -> seg.start() < s.end() && s.start() < seg.end()).toList();
    }

    private static void replace(List<Segment> covered, PlainTextEdits.Span s) {
        for (int i = 0; i < covered.size(); i++) {
            Segment seg = covered.get(i);
            String current = seg.t().getTextContent();
            int from = Math.max(0, s.start() - seg.start());
            int to = Math.min(seg.text().length(), s.end() - seg.start());
            String head = current.substring(0, from);
            String tail = current.substring(Math.min(to, current.length()));
            String middle = i == 0 ? s.edit().replacement() : "";
            seg.t().setTextContent(head + middle + tail);
            seg.t().setAttributeNS(XML_NS, "xml:space", "preserve");
        }
    }

    private static org.w3c.dom.Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        org.w3c.dom.Document d = f.newDocumentBuilder()
                .parse(new InputSource(new StringReader(new String(xml, StandardCharsets.UTF_8))));
        d.setXmlStandalone(true);
        return d;
    }

    private static byte[] serialize(org.w3c.dom.Document dom) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.STANDALONE, "yes");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        t.transform(new DOMSource(dom), new StreamResult(out));
        return out.toByteArray();
    }

    static byte[] readEntry(byte[] zip, String name) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                if (name.equals(e.getName())) return in.readAllBytes();
            }
        }
        return null;
    }

    private static byte[] writeEntry(byte[] zip, String name, byte[] content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip));
             ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                out.putNextEntry(new ZipEntry(e.getName()));
                out.write(name.equals(e.getName()) ? content : in.readAllBytes());
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}