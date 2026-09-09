package com.ai.llm.cache;

import com.ai.llm.ollama.OllamaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * 임베딩 코사인 유사도 기반 캐시.
 *
 * 기존 CacheService(정확 일치 캐시)는 "완전히 똑같은 문자열"일 때만 히트되는데,
 * 이 서비스는 "말은 다르지만 의미가 비슷한 질문"도 히트시킵니다.
 * 예: "파마리서치 직원수는?" ↔ "파마리서치 직원이 몇 명이야?" → 둘 다 히트 대상.
 *
 * 저장 구조: Redis Key = "semcache:{store}:{UUID}", Value = {question, embedding, answer}를 JSON으로 직렬화.
 * 조회 시: 질문을 임베딩 → 같은 store의 저장된 항목 전체와 코사인 유사도 비교 → 임계값 이상이면 히트.
 *
 * 규모가 작을 때(캐시 항목 수백~수천 건)를 전제로 한 브루트포스 비교입니다.
 * 항목 수가 아주 많아지면(수만 건 이상) OpenSearch/pgvector의 벡터 인덱스처럼
 * 근사 최근접 이웃(ANN) 검색으로 바꿔야 하지만, 캐시 용도로는 이 정도 규모를
 * 넘어서는 경우가 드물어 지금 단계에서는 과설계라고 판단했습니다.
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);
    private static final String KEY_PREFIX = "semcache:";
    // 프로젝트 내 다른 서비스(OllamaService 등)와 동일하게, 스프링 빈 대신 직접 생성해서 사용.
    // (이 프로젝트에는 ObjectMapper가 자동 구성 빈으로 등록되어 있지 않음)
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;
    private final OllamaService ollamaService;

    @Value("${semantic-cache.similarity-threshold:0.95}")
    private double similarityThreshold;

    public SemanticCacheService(StringRedisTemplate redisTemplate, OllamaService ollamaService) {
        this.redisTemplate = redisTemplate;
        this.ollamaService = ollamaService;
    }

    /**
     * 조회 결과. 히트했으면 answer가 채워져 있고, 미스여도 queryEmbedding은 항상 채워져 있습니다.
     * (미스였을 경우, 이 임베딩을 store()에 그대로 넘기면 임베딩을 다시 계산하지 않아도 됩니다.)
     */
    public static class LookupResult {
        private final boolean hit;
        private final String answer;
        private final float[] queryEmbedding;
        private final double bestScore;

        LookupResult(boolean hit, String answer, float[] queryEmbedding, double bestScore) {
            this.hit = hit;
            this.answer = answer;
            this.queryEmbedding = queryEmbedding;
            this.bestScore = bestScore;
        }

        public boolean isHit() {
            return hit;
        }

        public String getAnswer() {
            return answer;
        }

        public float[] getQueryEmbedding() {
            return queryEmbedding;
        }

        public double getBestScore() {
            return bestScore;
        }
    }

    /** 질문과 의미가 비슷한 캐시 항목이 있는지 찾습니다. Redis 장애 시에도 앱이 죽지 않도록 예외를 흡수합니다. */
    public LookupResult find(String question, String store) {
        float[] queryEmbedding;
        try {
            queryEmbedding = ollamaService.embed(question);
        } catch (Exception e) {
            log.warn("[SemanticCache] 임베딩 계산 실패, 시맨틱 캐시 건너뜀: {}", e.getMessage());
            return new LookupResult(false, null, null, -1);
        }

        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + store.toLowerCase() + ":*");
            if (keys == null || keys.isEmpty()) {
                return new LookupResult(false, null, queryEmbedding, -1);
            }

            String bestAnswer = null;
            double bestScore = -1;

            for (String key : keys) {
                String json = redisTemplate.opsForValue().get(key);
                if (json == null) continue;

                SemanticCacheEntry entry = objectMapper.readValue(json, SemanticCacheEntry.class);
                double score = cosineSimilarity(queryEmbedding, entry.getEmbedding());
                if (score > bestScore) {
                    bestScore = score;
                    bestAnswer = entry.getAnswer();
                }
            }

            if (bestScore >= similarityThreshold) {
                log.info("[SemanticCache] HIT (store={}, 유사도={})", store, String.format("%.4f", bestScore));
                return new LookupResult(true, bestAnswer, queryEmbedding, bestScore);
            }

            log.info("[SemanticCache] MISS (store={}, 최고 유사도={}, 임계값={})", store, String.format("%.4f", bestScore), similarityThreshold);
            return new LookupResult(false, null, queryEmbedding, bestScore);

        } catch (Exception e) {
            log.warn("[SemanticCache] 조회 실패, 캐시 없이 진행: {}", e.getMessage());
            return new LookupResult(false, null, queryEmbedding, -1);
        }
    }

    /**
     * 질문/답변을 캐시에 저장합니다.
     * queryEmbedding은 find() 호출 때 이미 계산된 걸 그대로 재사용합니다(임베딩 API 재호출 방지).
     */
    public void store(String question, String store, String answer, float[] queryEmbedding, Duration ttl) {
        if (queryEmbedding == null) {
            log.warn("[SemanticCache] 임베딩이 없어 저장을 건너뜀 (question={})", question);
            return;
        }
        try {
            SemanticCacheEntry entry = new SemanticCacheEntry(question, queryEmbedding, answer);
            String json = objectMapper.writeValueAsString(entry);
            String key = KEY_PREFIX + store.toLowerCase() + ":" + UUID.randomUUID();
            redisTemplate.opsForValue().set(key, json, ttl);
            log.info("[SemanticCache] SET (store={}, TTL={}분)", store, ttl.toMinutes());
        } catch (Exception e) {
            log.warn("[SemanticCache] 저장 실패, 무시하고 진행: {}", e.getMessage());
        }
    }

    private static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return -1;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}