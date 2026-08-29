package com.ai.llm.rag;

import com.ai.llm.cache.CacheService;
import com.ai.llm.pgvector.PgVectorRagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class RagController {

    // 완전히 동일한 질문이 짧은 시간 안에 반복되면(데모 재시연, 반복 테스트 등)
    // LLM을 다시 호출하지 않고 캐시된 답변을 즉시 반환합니다.
    // TTL을 짧게(5분) 두어, 문서가 재업로드된 뒤에도 오래된 답변이 계속 나오는 위험을 최소화합니다.
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RagService ragService;
    private final PgVectorRagService pgVectorRagService;
    private final CacheService cacheService;

    public RagController(RagService ragService, PgVectorRagService pgVectorRagService, CacheService cacheService) {
        this.ragService = ragService;
        this.pgVectorRagService = pgVectorRagService;
        this.cacheService = cacheService;
    }

    // store=opensearch(기본값) 또는 store=pgvector 로 두 벡터스토어를 비교 테스트할 수 있습니다.
    @GetMapping("/api/rag/ask")
    public String ask(@RequestParam String question,
                      @RequestParam(defaultValue = "opensearch") String store) {

        String cacheKey = "rag:" + store.toLowerCase() + ":" + question.trim().toLowerCase();

        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String answer = "pgvector".equalsIgnoreCase(store)
                ? pgVectorRagService.ask(question)
                : ragService.ask(question);

        cacheService.put(cacheKey, answer, CACHE_TTL);
        return answer;
    }
}