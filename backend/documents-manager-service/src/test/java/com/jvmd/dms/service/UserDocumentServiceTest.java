package com.jvmd.dms.service;

import com.jvmd.dms.entity.UserDocument;
import com.jvmd.dms.repository.UserDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDocumentServiceTest {

    private final UserDocumentRepository userDocumentRepository = mock(UserDocumentRepository.class);
    private final DocumentStorageService documentStorageService = mock(DocumentStorageService.class);
    private final DocumentIngestionJob ingestionJob = mock(DocumentIngestionJob.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final UserDocumentService service = new UserDocumentService(userDocumentRepository, documentStorageService, ingestionJob, vectorStore);

    @Test
    void searchUsesDocumentSpecificSettingsOnlyWhenDocumentIdHasText() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        service.searchInUserDocuments("договор", "user-1", " ");

        verify(vectorStore).similaritySearch(org.mockito.ArgumentMatchers.<SearchRequest>argThat(request ->
                request.getTopK() == 12
                        && request.getSimilarityThreshold() == 0.7
                        && request.getFilterExpression().toString().contains("user-1")
                        && !request.getFilterExpression().toString().contains("documentId")
        ));
    }

    @Test
    void searchWidensRecallForSpecificDocument() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document("text")));

        List<Document> result = service.searchInUserDocuments("договор", "user-1", "doc-1");

        assertThat(result).hasSize(1);
        verify(vectorStore).similaritySearch(org.mockito.ArgumentMatchers.<SearchRequest>argThat(request ->
                request.getTopK() == 20
                        && request.getSimilarityThreshold() == 0.0
                        && request.getFilterExpression().toString().contains("doc-1")
        ));
    }

    @Test
    void rejectsBlankSearchArguments() {
        assertThatThrownBy(() -> service.searchInUserDocuments(" ", "user-1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("query is required.");

        assertThatThrownBy(() -> service.searchInUserDocuments("договор", " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId is required.");
    }

    @Test
    void uploadPersistsDocumentAndStartsAsyncIngestion() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.txt",
                "text/plain",
                "Текст договора".getBytes(StandardCharsets.UTF_8)
        );
        when(documentStorageService.store(any(), eq("user-1"), same(file))).thenReturn("user-1/doc-1/contract.txt");
        when(userDocumentRepository.save(any(UserDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String documentId = service.uploadUserDocument(file, "user-1");

        verify(userDocumentRepository).save(org.mockito.ArgumentMatchers.<UserDocument>argThat(document ->
                document.getId().equals(documentId)
                        && document.getUserId().equals("user-1")
                        && document.getStatus() == DocumentProcessingStatus.INDEXING
                        && document.getObjectName().equals("user-1/doc-1/contract.txt")
        ));
        verify(ingestionJob).indexAsync(documentId);
    }

    @Test
    void reindexAndDeleteUsePersistentDocument() {
        UserDocument document = new UserDocument();
        document.setId("doc-1");
        document.setUserId("user-1");
        document.setFileName("contract.txt");
        document.setObjectName("user-1/doc-1/contract.txt");
        document.setStatus(DocumentProcessingStatus.READY);
        when(userDocumentRepository.findByIdAndUserId("doc-1", "user-1")).thenReturn(Optional.of(document));

        service.reindexDocument("doc-1", "user-1");
        service.deleteDocument("doc-1", "user-1");

        assertThat(document.getStatus()).isEqualTo(DocumentProcessingStatus.DELETED);
        verify(vectorStore, atLeastOnce()).delete(org.mockito.ArgumentMatchers.contains("doc-1"));
        verify(documentStorageService).delete("user-1/doc-1/contract.txt");
        verify(ingestionJob).indexAsync("doc-1");
        verify(userDocumentRepository, atLeastOnce()).save(document);
    }
}
