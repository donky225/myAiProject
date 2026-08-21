package com.ai.llm.opensearch.dto;

// 저장할 문서 하나
public record RagDocument(String id, String title, String content, float[] embedding) {

}