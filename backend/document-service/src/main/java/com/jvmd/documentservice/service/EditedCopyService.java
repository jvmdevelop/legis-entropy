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

    private record Rendered(EditResult result, String ext, String contentType) {}

    private UserDocument requireDoc(String documentId, String userId, List<TextEdit> edits) {
        if (edits == null || edits.isEmpty()) throw new IllegalArgumentException("edits are required.");
        return repository.findByIdAndUserId(documentId, userId)
                .filter(d -> d.getStatus() != DocumentProcessingStatus.DELETED)
                .orElseThrow(() -> new IllegalArgumentException("Document not found."));
    }

    private Rendered render(UserDocument doc, List<TextEdit> edits) throws Exception {
        String name = doc.getFileName() == null ? "document" : doc.getFileName();
        String lower = name.toLowerCase(Locale.ROOT);
        String type = doc.getContentType() == null ? "" : doc.getContentType();
        if (lower.endsWith(".docx") || DOCX.equals(type)) {
            StoredObject original = storage.load(doc.getObjectName(), name, type);
            return new Rendered(DocxEditor.apply(original.content(), edits), "docx", DOCX);
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || type.startsWith("text/")) {
            StoredObject original = storage.load(doc.getObjectName(), name, type);
            return new Rendered(editText(new String(original.content(), StandardCharsets.UTF_8), edits, false),
                    lower.endsWith(".md") ? "md" : "txt", "text/plain; charset=utf-8");
        }
        String text = doc.getPlainText() == null ? "" : doc.getPlainText();
        return new Rendered(editText(text, edits, true), "docx", DOCX);
    }

    public Result create(String documentId, String userId, List<TextEdit> edits, String fileName) throws Exception {
        UserDocument doc = requireDoc(documentId, userId, edits);
        String name = doc.getFileName() == null ? "document" : doc.getFileName();
        Rendered r = render(doc, edits);
        if (r.result().applied().isEmpty()) {
            return new Result(null, null, r.ext(), r.result().applied(), r.result().skipped());
        }
        String outName = fileName != null && !fileName.isBlank() ? fileName : editedName(name, r.ext());
        String newId = documents.uploadUserDocument(
                new StoredMultipartFile(outName, r.contentType(), r.result().content()), userId);
        log.info("Edited copy {} of document {} for user {}: {} edit(s) applied, {} skipped",
                newId, documentId, userId, r.result().applied().size(), r.result().skipped().size());
        return new Result(newId, outName, r.ext(), r.result().applied(), r.result().skipped());
    }

    public Result applyInPlace(String documentId, String userId, List<TextEdit> edits) throws Exception {
        UserDocument doc = requireDoc(documentId, userId, edits);
        if (doc.getStatus() == DocumentProcessingStatus.INDEXING) {
            throw new IllegalStateException("Document is being indexed, try again later.");
        }
        String name = doc.getFileName() == null ? "document" : doc.getFileName();
        Rendered r = render(doc, edits);
        if (r.result().applied().isEmpty()) {
            return new Result(null, null, r.ext(), r.result().applied(), r.result().skipped());
        }
        String outName = name.toLowerCase(Locale.ROOT).endsWith("." + r.ext())
                ? name : name.replaceFirst("\\.[A-Za-z0-9]{1,5}$", "") + "." + r.ext();
        byte[] content = r.result().content();
        String stored = storage.store(documentId, userId,
                new StoredMultipartFile("v" + System.currentTimeMillis() + "_" + outName, r.contentType(), content));
        String text = documents.extractText(new StoredMultipartFile(outName, r.contentType(), content));
        doc.setPreviousObjectName(doc.getObjectName());
        doc.setObjectName(stored);
        doc.setFileName(outName);
        doc.setContentType(r.contentType());
        doc.setPlainText(text);
        repository.save(doc);
        documents.reindexDocument(documentId, userId);
        log.info("Document {} edited in place for user {}: {} edit(s) applied, {} skipped",
                documentId, userId, r.result().applied().size(), r.result().skipped().size());
        return new Result(documentId, outName, r.ext(), r.result().applied(), r.result().skipped());
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