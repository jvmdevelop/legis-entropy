package com.jvmd.dms.template.service;

import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class TemplateConversionService {

    public record ConversionResult(
        String sourceFormat,
        String canonicalMarkdown
    ) {}

    public ConversionResult convert(MultipartFile file) {
        String name =
            file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename();
        String ext = extension(name);
        String format = formatOf(ext);

        try {
            String text;
            if ("MD".equals(format) || "TXT".equals(format)) {
                text = new String(
                    file.getBytes(),
                    java.nio.charset.StandardCharsets.UTF_8
                );
            } else {
                ByteArrayResource resource = new ByteArrayResource(
                    file.getBytes()
                ) {
                    @Override
                    public String getFilename() {
                        return name.isBlank()
                            ? "template." + ext.toLowerCase(Locale.ROOT)
                            : name;
                    }
                };
                TikaDocumentReader reader = new TikaDocumentReader(resource);
                List<Document> docs = reader.get();
                StringBuilder sb = new StringBuilder();
                for (Document doc : docs) {
                    if (sb.length() > 0) sb.append("\n\n");
                    sb.append(doc.getText());
                }
                text = sb.toString();
            }
            return new ConversionResult(format, normalise(text));
        } catch (Exception e) {
            log.error(
                "Template conversion failed for {}: {}",
                name,
                e.getMessage(),
                e
            );
            throw new IllegalStateException(
                "Не удалось распознать шаблон: " + e.getMessage(),
                e
            );
        }
    }

    private static String normalise(String raw) {
        if (raw == null) return "";
        String trimmed = raw.replace("\r\n", "\n").replace('\r', '\n');
        trimmed = trimmed.replaceAll("\n{3,}", "\n\n");
        StringBuilder out = new StringBuilder(trimmed.length());
        for (String line : trimmed.split("\n", -1)) {
            int end = line.length();
            while (end > 0 && Character.isWhitespace(line.charAt(end - 1)))
                end--;
            out.append(line, 0, end).append('\n');
        }
        return out.toString().strip();
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot >= filename.length() - 1) return "";
        return filename.substring(dot + 1);
    }

    private static String formatOf(String ext) {
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "docx", "doc" -> "DOCX";
            case "pdf" -> "PDF";
            case "md", "markdown" -> "MD";
            case "txt" -> "TXT";
            case "html", "htm" -> "HTML";
            case "odt" -> "ODT";
            case "rtf" -> "RTF";
            default -> "OTHER";
        };
    }
}
