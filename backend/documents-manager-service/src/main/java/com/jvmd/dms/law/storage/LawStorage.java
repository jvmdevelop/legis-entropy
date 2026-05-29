package com.jvmd.dms.law.storage;

import com.jvmd.dms.law.model.Law;
import lombok.AllArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@AllArgsConstructor
public class LawStorage {

    private final VectorStore vectorStore;

    public void save(Law law) {
        Document doc = new Document(law.getContent(), law.getMetaData());
        vectorStore.add(List.of(doc));
    }

    public List<Document> search(SearchRequest query) {
        return vectorStore.similaritySearch(SearchRequest.from(query).topK(10).build());
    }

}
