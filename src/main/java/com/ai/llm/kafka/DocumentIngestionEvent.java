package com.ai.llm.kafka;

/**
 * 문서 인제스트 요청 이벤트. 파일 자체(바이트)가 아니라 임시 저장 경로만 담아서
 * Kafka 메시지 크기를 작게 유지합니다.
 */
public record DocumentIngestionEvent(
        String jobId,
        String tempFilePath,
        String originalFilename,
        String store // "pgvector" 또는 "opensearch"
) {
}
