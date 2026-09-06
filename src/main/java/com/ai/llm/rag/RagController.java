package com.ai.llm.rag;

import com.ai.llm.cache.CacheService;
import com.ai.llm.pgvector.PgVectorRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

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

        log.info(">>> GET /api/rag/ask (store={}) 질문: \"{}\"", store, question);
        String cacheKey = "rag:" + store.toLowerCase() + ":" + question.trim().toLowerCase();

        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            log.info("<<< 캐시 히트, LLM 호출 없이 즉시 응답");
            return cached;
        }

        String answer = "pgvector".equalsIgnoreCase(store)
                ? pgVectorRagService.ask(question)
                : ragService.ask(question);

        cacheService.put(cacheKey, answer, CACHE_TTL);
        log.info("<<< 응답 완료 및 캐시 저장 (TTL={}분)", CACHE_TTL.toMinutes());
        return answer;
    }

    /**
     * ask()와 동일한 검색/리랭킹/프롬프트 로직을 쓰되, LLM 생성 결과를 토큰이 도착하는 대로
     * Server-Sent Events(SSE)로 스트리밍합니다. 프론트엔드는 EventSource로 구독합니다.
     *
     * 스트림 끝에 STREAM_DONE_MARKER를 명시적으로 하나 더 흘려보냅니다.
     * (EventSource는 서버가 연결을 닫으면 기본적으로 "자동 재연결"을 시도하는데, 이때
     *  readyState가 CLOSED가 아니라 CONNECTING이 되어 정상 종료와 실제 에러를 구분하기
     *  어렵습니다. 그래서 브라우저의 연결 종료 감지에 기대지 않고, 이 명시적 마커를
     *  클라이언트가 직접 보고 종료 처리하도록 합니다.)
     *
     * 참고: 이 엔드포인트는 캐싱을 적용하지 않습니다. 부분 토큰 스트림을 캐시했다가
     * 그대로 재생하는 것은 복잡도 대비 이득이 적어, 캐싱이 필요한 경우 기존
     * /api/rag/ask(논스트리밍)를 사용하도록 분리했습니다.
     */
    public static final String STREAM_DONE_MARKER = "[[STREAM_DONE]]";

    @GetMapping(value = "/api/rag/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStream(@RequestParam String question,
                                  @RequestParam(defaultValue = "opensearch") String store) {
        log.info(">>> GET /api/rag/ask/stream (store={}) 질문: \"{}\"", store, question);
        Flux<String> tokens = "pgvector".equalsIgnoreCase(store)
                ? pgVectorRagService.askStream(question)
                : ragService.askStream(question);

        return tokens
                .doOnComplete(() -> log.info("<<< 스트리밍 완료 (store={})", store))
                .doOnError(e -> log.error("<<< 스트리밍 중 에러 (store={}): {}", store, e.getMessage()))
                .concatWith(Flux.just(STREAM_DONE_MARKER));
    }
}