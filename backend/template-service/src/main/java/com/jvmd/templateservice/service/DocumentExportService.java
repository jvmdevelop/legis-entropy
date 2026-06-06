package com.jvmd.templateservice.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
@Slf4j
public class DocumentExportService {

    private final Parser mdParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();

    public byte[] toPdf(String title, String markdown) throws IOException {
        String html = wrapHtml(title, htmlRenderer.render(mdParser.parse(markdown)));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        }
    }

    public byte[] toDocx(String title, String markdown) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            addHeading(doc, title, 16);
            walkDocx(doc, mdParser.parse(markdown));
            doc.write(out);
            return out.toByteArray();
        }
    }

    private void walkDocx(XWPFDocument doc, Node parent) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading h) {
                int size = switch (h.getLevel()) { case 1 -> 14; case 2 -> 13; default -> 12; };
                addHeading(doc, extractText(h), size);
            } else if (node instanceof Paragraph p) {
                XWPFParagraph para = doc.createParagraph();
                appendInlines(para, p);
            } else if (node instanceof BulletList bl) {
                for (Node item = bl.getFirstChild(); item != null; item = item.getNext()) {
                    XWPFParagraph para = doc.createParagraph();
                    para.setIndentationLeft(480);
                    para.createRun().setText("• " + extractText(item));
                }
            } else if (node instanceof OrderedList ol) {
                int idx = ol.getStartNumber();
                for (Node item = ol.getFirstChild(); item != null; item = item.getNext()) {
                    XWPFParagraph para = doc.createParagraph();
                    para.setIndentationLeft(480);
                    para.createRun().setText((idx++) + ". " + extractText(item));
                }
            } else if (node instanceof FencedCodeBlock cb) {
                XWPFParagraph para = doc.createParagraph();
                XWPFRun run = para.createRun();
                run.setFontFamily("Courier New");
                run.setFontSize(10);
                run.setText(cb.getLiteral());
            } else {
                walkDocx(doc, node);
            }
        }
    }

    private void appendInlines(XWPFParagraph para, Node parent) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Text t) { para.createRun().setText(t.getLiteral()); }
            else if (node instanceof StrongEmphasis se) { XWPFRun r = para.createRun(); r.setBold(true); r.setText(extractText(se)); }
            else if (node instanceof Emphasis em) { XWPFRun r = para.createRun(); r.setItalic(true); r.setText(extractText(em)); }
            else if (node instanceof Code c) { XWPFRun r = para.createRun(); r.setFontFamily("Courier New"); r.setFontSize(10); r.setText(c.getLiteral()); }
            else if (node instanceof HardLineBreak || node instanceof SoftLineBreak) { para.createRun().addBreak(); }
            else { appendInlines(para, node); }
        }
    }

    private void addHeading(XWPFDocument doc, String text, int fontSize) {
        XWPFParagraph para = doc.createParagraph();
        XWPFRun run = para.createRun();
        run.setBold(true);
        run.setFontSize(fontSize);
        run.setText(text);
        run.addBreak();
    }

    private String extractText(Node node) {
        StringBuilder sb = new StringBuilder();
        collectText(node, sb);
        return sb.toString().trim();
    }

    private void collectText(Node node, StringBuilder sb) {
        if (node instanceof Text t) { sb.append(t.getLiteral()); return; }
        if (node instanceof Code c) { sb.append(c.getLiteral()); return; }
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) { sb.append(' '); return; }
        for (Node c = node.getFirstChild(); c != null; c = c.getNext()) collectText(c, sb);
    }

    private String wrapHtml(String title, String body) {
        return """
        <!DOCTYPE html>
        <html><head><meta charset="UTF-8"/>
        <style>
          body { font-family: Arial, sans-serif; font-size: 12pt; margin: 40px; color: #222; }
          h1,h2,h3 { color: #1a1a2e; margin-top: 1.2em; }
          p  { line-height: 1.7; margin: .5em 0; }
          ul,ol { padding-left: 1.5em; }
          li { margin: .2em 0; }
          code { background: #f4f4f4; padding: 2px 5px; border-radius: 3px; font-size: 10pt; }
          pre  { background: #f4f4f4; padding: 12px; border-radius: 4px; }
        </style>
        <title>%s</title></head>
        <body><h1>%s</h1>%s</body></html>
        """.formatted(esc(title), esc(title), body);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
