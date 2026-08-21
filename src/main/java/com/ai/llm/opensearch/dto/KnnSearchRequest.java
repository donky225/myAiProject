package com.ai.llm.opensearch.dto;

// KNN 검색 요청: { "size": 3, "query": { "knn": { "embedding": { "vector": [...], "k": 3 } } } }
public record KnnSearchRequest(int size, Query query) {

    public record Query(Knn knn) {}
    public record Knn(EmbeddingClause embedding) {}
    public record EmbeddingClause(float[] vector, int k) {}

    public static KnnSearchRequest of(float[] vector, int k) {
        return new KnnSearchRequest(k, new Query(new Knn(new EmbeddingClause(vector, k))));
    }
}