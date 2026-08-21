package com.ai.llm.pgvector;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PgVectorService {

    private final VectorStore vectorStore;

    // OpenSearchVectorStoreAutoConfiguration을 제외했으므로
    // VectorStore 타입 빈은 pgvector 하나뿐입니다. Qualifier 불필요.
    public PgVectorService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> search(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .build();

        return vectorStore.similaritySearch(request);
    }

    // 청킹된 문서를 pgvector에 저장합니다. 임베딩은 VectorStore가
    // 내부적으로 구성된 EmbeddingModel(ollama qwen3-embedding:0.6b)을 통해 자동 생성합니다.
    public void add(List<Document> documents) {
        vectorStore.add(documents);
    }
}
