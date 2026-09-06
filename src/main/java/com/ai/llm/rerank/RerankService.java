package com.ai.llm.rerank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * 벡터 검색으로 얻은 후보 문서를 cross-encoder 리랭커(FastAPI, BAAI/bge-reranker-v2-m3)로
 * 재정렬합니다. 벡터 유사도(bi-encoder)는 쿼리와 문서를 독립적으로 임베딩해 비교하는 반면,
 * cross-encoder는 쿼리-문서 쌍을 함께 보고 관련성을 판단해 더 정확한 순위를 냅니다.
 *
 * 리랭크 서비스가 다운되어도 RAG 파이프라인 전체가 죽지 않도록,
 * 호출 실패 시 원본 순서(벡터 유사도 순)로 폴백합니다.
 */
@Service
public class RerankService {

    private static final Logger log = LoggerFactory.getLogger(RerankService.class);

    private final RestClient restClient;
    private final boolean enabled;
    private final int topK;

    public RerankService(
            RestClient.Builder restClientBuilder,
            @Value("${rerank.service-url:http://localhost:8002}") String serviceUrl,
            @Value("${rerank.enabled:true}") boolean enabled,
            @Value("${rerank.top-k:3}") int topK
    ) {
        this.restClient = restClientBuilder.baseUrl(serviceUrl).build();
        this.enabled = enabled;
        this.topK = topK;
        log.info("RerankService 초기화 완료 (enabled={}, url={}, topK={})", enabled, serviceUrl, topK);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public record RerankDoc(String id, String text) {}
    public record RerankedResult(String id, String text, double score) {}
    private record RerankRequest(String query, List<RerankDoc> documents, int top_k) {}
    private record RerankResponse(List<RerankedResult> results) {}

    /**
     * @return 리랭크된 결과 (점수 내림차순, 최대 top_k개). 서비스 호출 실패 시 빈 리스트 반환
     *         (호출부에서 빈 리스트를 받으면 원본 순서로 폴백해야 함).
     */
    public List<RerankedResult> rerank(String query, List<RerankDoc> documents) {
        if (!enabled || documents.isEmpty()) {
            log.debug("리랭크 스킵 (enabled={}, 후보 문서 수={})", enabled, documents.size());
            return List.of();
        }
        long start = System.currentTimeMillis();
        try {
            RerankRequest request = new RerankRequest(query, documents, topK);
            RerankResponse response = restClient.post()
                    .uri("/rerank")
                    .body(request)
                    .retrieve()
                    .body(RerankResponse.class);

            long elapsed = System.currentTimeMillis() - start;
            int resultCount = response != null ? response.results().size() : 0;
            log.info("리랭크 성공: 후보 {}건 → 결과 {}건 ({}ms) [질문: \"{}\"]",
                    documents.size(), resultCount, elapsed, truncate(query));

            return response != null ? response.results() : List.of();
        } catch (Exception e) {
            // 리랭크 서비스 장애 시 RAG 자체는 계속 동작해야 하므로 예외를 흡수하고 폴백
            log.warn("리랭크 서비스 호출 실패, 코사인 유사도 폴백으로 전환: {}", e.getMessage());
            return List.of();
        }
    }

    private String truncate(String text) {
        return text.length() > 30 ? text.substring(0, 30) + "..." : text;
    }
}