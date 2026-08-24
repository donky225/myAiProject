package com.ai.llm.ollama;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

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

    private static final String OLLAMA_STREAM_URL = "http://localhost:11434/api/generate";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    public OllamaService(ChatModel chatModel, EmbeddingModel embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    /** 텍스트를 벡터로 변환합니다. */
    public float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        return response.getResults().get(0).getOutput();
    }

    /** 프롬프트를 Qwen3 4B에 전달하고 답변 텍스트를 받습니다. */
    public String generate(String prompt) {
        return chatModel.call(prompt);
    }

// 보통 http://localhost:11434 이거나 application.yml의 spring.ai.ollama.base-url 값입니다):

    /**
     * Ollama의 스트리밍 응답(stream=true)을 받아, 토큰이 도착할 때마다 onToken 콜백을 호출합니다.
     * 실시간 음성 대화에서 "문장이 완성되는 대로 바로 TTS로 넘기기" 위해 사용합니다.
     */
    public void generateStream(String prompt, Consumer<String> onToken) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", "qwen3:4b",
                    "prompt", prompt,
                    "stream", true
            );
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_STREAM_URL))
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
                        onToken.accept(token);
                    }
                } catch (Exception e) {
                    // 개별 라인 파싱 실패는 무시하고 계속 진행
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("Ollama 스트리밍 호출 실패: " + e.getMessage(), e);
        }
    }

}