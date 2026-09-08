package com.ai.llm.cache;

import com.ai.llm.ollama.OllamaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SemanticCacheService의 핵심 로직(코사인 유사도 기반 히트/미스 판정)에 대한 단위 테스트.
 * Redis, Ollama 둘 다 목(mock) 처리해서 외부 서비스 없이 GitHub Actions에서 그대로 동작합니다.
 */
class SemanticCacheServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private OllamaService ollamaService;
    private SemanticCacheService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        ollamaService = mock(OllamaService.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new SemanticCacheService(redisTemplate, ollamaService, objectMapper);
        // @Value로 주입되는 필드는 Spring 컨테이너 밖에서는 채워지지 않으므로 테스트에서 직접 설정
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.95);
    }

    @Test
    void 저장된_항목이_없으면_미스이지만_쿼리_임베딩은_반환한다() throws Exception {
        float[] queryEmbedding = {1f, 0f, 0f};
        when(ollamaService.embed("질문")).thenReturn(queryEmbedding);
        when(redisTemplate.keys(anyString())).thenReturn(Set.of());

        SemanticCacheService.LookupResult result = service.find("질문", "opensearch");

        assertThat(result.isHit()).isFalse();
        assertThat(result.getQueryEmbedding()).isEqualTo(queryEmbedding);
    }

    @Test
    void 저장된_벡터와_거의_동일하면_히트한다() throws Exception {
        float[] queryEmbedding = {1f, 0f, 0f};
        float[] storedEmbedding = {1f, 0f, 0f}; // 완전히 동일 → 코사인 유사도 1.0

        when(ollamaService.embed("비슷한 질문")).thenReturn(queryEmbedding);

        SemanticCacheEntry entry = new SemanticCacheEntry("원래 질문", storedEmbedding, "저장된 답변");
        String json = objectMapper.writeValueAsString(entry);

        when(redisTemplate.keys("semcache:opensearch:*")).thenReturn(Set.of("semcache:opensearch:abc"));
        when(valueOperations.get("semcache:opensearch:abc")).thenReturn(json);

        SemanticCacheService.LookupResult result = service.find("비슷한 질문", "opensearch");

        assertThat(result.isHit()).isTrue();
        assertThat(result.getAnswer()).isEqualTo("저장된 답변");
        assertThat(result.getBestScore()).isGreaterThanOrEqualTo(0.95);
    }

    @Test
    void 저장된_벡터와_많이_다르면_미스한다() throws Exception {
        float[] queryEmbedding = {1f, 0f, 0f};
        float[] storedEmbedding = {0f, 1f, 0f}; // 직교 벡터 → 코사인 유사도 0.0

        when(ollamaService.embed("전혀 다른 질문")).thenReturn(queryEmbedding);

        SemanticCacheEntry entry = new SemanticCacheEntry("원래 질문", storedEmbedding, "저장된 답변");
        String json = objectMapper.writeValueAsString(entry);

        when(redisTemplate.keys("semcache:opensearch:*")).thenReturn(Set.of("semcache:opensearch:abc"));
        when(valueOperations.get("semcache:opensearch:abc")).thenReturn(json);

        SemanticCacheService.LookupResult result = service.find("전혀 다른 질문", "opensearch");

        assertThat(result.isHit()).isFalse();
    }

    @Test
    void store는_전달받은_임베딩을_재사용하고_Redis에_TTL과_함께_저장한다() {
        float[] embedding = {0.5f, 0.5f, 0f};

        service.store("질문", "opensearch", "답변", embedding, Duration.ofMinutes(5));

        // embed()가 다시 호출되지 않았는지 확인 (find()에서 이미 계산한 임베딩을 재사용해야 함)
        org.mockito.Mockito.verifyNoInteractions(ollamaService);
        org.mockito.Mockito.verify(valueOperations)
                .set(anyString(), anyString(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
    }
}