package com.jvmd.dms;

import com.jvmd.dms.repository.UserDocumentRepository;
import com.jvmd.dms.service.DocumentStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "spring.flyway.enabled=false"
})
class DocumentsManagerServiceApplicationTests {

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
