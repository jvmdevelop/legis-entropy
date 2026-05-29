package com.jvmd.dms.document.reader;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class DocxDocumentFormatReader implements DocumentFormatReader {

    @Override
    public boolean supports(String filename, String contentType) {
        return filename.endsWith(".docx")
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType);
    }

    @Override
    public List<Document> read(MultipartFile file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    String xml = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    String text = extractDocxText(xml).trim();
                    if (text.isBlank()) {
                        throw new IllegalArgumentException("Uploaded DOCX file is empty.");
                    }
                    return List.of(new Document(text));
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read uploaded DOCX file.", e);
        }
        throw new IllegalArgumentException("Could not read uploaded DOCX file.");
    }

    private String extractDocxText(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        org.w3c.dom.Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList paragraphs = document.getElementsByTagNameNS("*", "p");

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < paragraphs.getLength(); i++) {
            NodeList children = paragraphs.item(i).getChildNodes();
            appendTextNodes(children, result);
            result.append('\n');
        }
        return result.toString();
    }

    private void appendTextNodes(NodeList nodes, StringBuilder result) {
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Node node = nodes.item(i);
            String localName = node.getLocalName();
            if ("t".equals(localName)) {
                result.append(node.getTextContent());
            } else if ("tab".equals(localName)) {
                result.append('\t');
            } else if ("br".equals(localName)) {
                result.append('\n');
            }
            appendTextNodes(node.getChildNodes(), result);
        }
    }
}
