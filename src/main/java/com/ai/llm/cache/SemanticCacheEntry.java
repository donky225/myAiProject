package com.ai.llm.cache;

/**
 * 시맨틱 캐시 한 건을 표현합니다. Redis에는 이 객체를 JSON으로 직렬화해서 저장합니다.
 * (질문 원문은 디버깅/로그 확인용으로만 남겨두고, 실제 유사도 비교는 embedding으로만 합니다.)
 */
public class SemanticCacheEntry {

    private String question;
    private float[] embedding;
    private String answer;

    // Jackson이 JSON ↔ 객체 변환에 쓰는 기본 생성자
    public SemanticCacheEntry() {
    }

    public SemanticCacheEntry(String question, float[] embedding, String answer) {
        this.question = question;
        this.embedding = embedding;
        this.answer = answer;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}