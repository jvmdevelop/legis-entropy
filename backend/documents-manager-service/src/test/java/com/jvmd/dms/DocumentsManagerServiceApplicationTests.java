package com.jvmd.dms;

import com.jvmd.dms.repository.UserDocumentRepository;
import com.jvmd.dms.service.DocumentStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class DocumentsManagerServiceApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    private VectorStore vectorStore;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @MockitoBean
    private UserDocumentRepository userDocumentRepository;

    @MockitoBean
    private DocumentStorageService documentStorageService;

    @Test
    void contextLoads() {
    }

}
