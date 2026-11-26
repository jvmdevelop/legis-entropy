package com.jvmd.documentservice.service;

import com.jvmd.documentservice.edit.DocxEditor;
import com.jvmd.documentservice.edit.DocxWriter;
import com.jvmd.documentservice.edit.EditResult;
import com.jvmd.documentservice.edit.PlainTextEdits;
import com.jvmd.documentservice.edit.TextEdit;
import com.jvmd.documentservice.model.UserDocument;
import com.jvmd.documentservice.repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class EditedCopyService {
    static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final UserDocumentRepository repository;

    private final DocumentStorageService storage;

    private final UserDocumentService documents;

    public record Result(String documentId, String fileName, String format, List<Integer> applied,
                         List<EditResult.Skipped> skipped) {}

    public Result create(String documentId, String userId, List<TextEdit> edits, String fileName) throws Exception {
        if (edits == null || edits.isEmpty()) throw new IllegalArgumentException("edits are required.");
        UserDocument doc = repository.findByIdAndUserId(documentId, userId)
                .filter(d -> d.getStatus() != DocumentProcessingStatus.DELETED)
                .orElseThrow(() -> new IllegalArgumentException("Document not found."));
        String name = doc.getFileName() == null ? "document" : doc.getFileName();
        String lower = name.toLowerCase(Locale.ROOT);
        String type = doc.getContentType() == null ? "" : doc.getContentType();
        EditResult result;
        String ext;
        String contentType;
        if (lower.endsWith(".docx") || DOCX.equals(type)) {
            StoredObject original = storage.load(doc.getObjectName(), name, type);
            result = DocxEditor.apply(original.content(), edits);
            ext = "docx";
            contentType = DOCX;
        } else if (lower.endsWith(".txt") || lower.endsWith(".md") || type.startsWith("text/")) {
            StoredObject original = storage.load(doc.getObjectName(), name, type);
            result = editText(new String(original.content(), StandardCharsets.UTF_8), edits, false);
            ext = lower.endsWith(".md") ? "md" : "txt";
            contentType = "text/plain; charset=utf-8";
        } else {
            String text = doc.getPlainText() == null ? "" : doc.getPlainText();
            result = editText(text, edits, true);
            ext = "docx";
            contentType = DOCX;
        }
        if (result.applied().isEmpty()) {
            return new Result(null, null, ext, result.applied(), result.skipped());
        }
        String outName = fileName != null && !fileName.isBlank() ? fileName : editedName(name, ext);
        String newId = documents.uploadUserDocument(new StoredMultipartFile(outName, contentType, result.content()),
                userId);
        log.info("Edited copy {} of document {} for user {}: {} edit(s) applied, {} skipped",
                newId, documentId, userId, result.applied().size(), result.skipped().size());
        return new Result(newId, outName, ext, result.applied(), result.skipped());
    }

    private static EditResult editText(String text, List<TextEdit> edits, boolean asDocx) throws Exception {
        List<EditResult.Skipped> skipped = new ArrayList<>();
        List<PlainTextEdits.Span> spans = PlainTextEdits.resolve(text, edits, skipped);
        String out = PlainTextEdits.apply(text, spans);
        byte[] bytes = asDocx ? DocxWriter.fromText(out) : out.getBytes(StandardCharsets.UTF_8);
        return new EditResult(bytes, spans.stream().map(PlainTextEdits.Span::index).sorted().toList(), skipped);
    }

    static String editedName(String original, String ext) {
        String stem = original.replaceFirst("\\.[A-Za-z0-9]{1,5}$", "");
        return stem + " (исправлено)." + ext;
    }
}