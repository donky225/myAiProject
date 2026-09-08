package com.ai.llm.ollama;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    // application.yml의 spring.ai.ollama.base-url (환경변수 SPRING_AI_OLLAMA_BASE_URL로 오버라이드 가능)에서 주입.
    // 기본값은 로컬 개발 환경(IntelliJ에서 직접 실행) 호환을 위해 localhost 유지.
    private final String ollamaBaseUrl;

    public OllamaService(
            ChatModel chatModel,
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.ollama.base-url:http://localhost:11434}") String ollamaBaseUrl
    ) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.ollamaBaseUrl = ollamaBaseUrl;
        log.info("OllamaService 초기화: base-url={}", ollamaBaseUrl);
    }

    /** 텍스트를 벡터로 변환합니다. */
    public float[] embed(String text) {
        long start = System.currentTimeMillis();
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        log.debug("임베딩 생성 완료 ({}ms)", System.currentTimeMillis() - start);
        return response.getResults().get(0).getOutput();
    }

    /** 프롬프트를 Qwen3 4B에 전달하고 답변 텍스트를 받습니다. */
    public String generate(String prompt) {
        long start = System.currentTimeMillis();
        String result = chatModel.call(prompt);
        log.info("LLM 생성 완료 ({}ms, 프롬프트 {}자, 응답 {}자)", System.currentTimeMillis() - start, prompt.length(), result.length());
        return result;
    }

    /**
     * Ollama의 스트리밍 응답(stream=true)을 받아, 토큰이 도착할 때마다 onToken 콜백을 호출합니다.
     * 실시간 음성 대화에서 "문장이 완성되는 대로 바로 TTS로 넘기기" 위해 사용합니다.
     */
    public void generateStream(String prompt, Consumer<String> onToken) {
        log.info("Ollama 스트리밍 생성 시작 (프롬프트 {}자)", prompt.length());
        long start = System.currentTimeMillis();
        int[] tokenCount = {0};
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", "qwen3:4b",
                    "prompt", prompt,
                    "stream", true
            );
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());

            response.body().forEach(line -> {
                if (line.isBlank()) return;
                try {
                    JsonNode node = objectMapper.readTree(line);
                    String token = node.path("response").asText("");
                    if (!token.isEmpty()) {
                        tokenCount[0]++;
                        onToken.accept(token);
                    }
                } catch (Exception e) {
                    // 개별 라인 파싱 실패는 무시하고 계속 진행
                    log.debug("스트리밍 라인 파싱 실패(무시하고 계속): {}", e.getMessage());
                }
            });
            log.info("Ollama 스트리밍 생성 완료 ({}ms, 토큰 {}개)", System.currentTimeMillis() - start, tokenCount[0]);
        } catch (Exception e) {
            log.error("Ollama 스트리밍 호출 실패: {}", e.getMessage());
            throw new RuntimeException("Ollama 스트리밍 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * generateStream()의 블로킹 콜백 방식을 Flux&lt;String&gt;으로 감싼 버전.
     * SSE 컨트롤러(text/event-stream)에서 그대로 반환할 수 있도록 리액티브 타입으로 어댑팅합니다.
     * 기존 generateStream() 로직/실시간 음성 파이프라인 코드는 전혀 건드리지 않습니다.
     *
     * boundedElastic 스케줄러에서 블로킹 HTTP 스트리밍 호출을 실행해, 서블릿 요청 처리 스레드를
     * 블로킹하지 않게 합니다.
     */
    public Flux<String> generateStreamReactive(String prompt) {
        return Flux.<String>create(sink -> {
            try {
                generateStream(prompt, sink::next);
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

}