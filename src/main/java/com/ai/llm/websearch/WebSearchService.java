package com.ai.llm.websearch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WebSearchService {

    private final RestClient restClient;
    private final String apiKey;

    public WebSearchService(@Value("${tavily.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.tavily.com")
                .build();
    }

    /**
     * Tavily로 실시간 웹 검색을 수행하고, 상위 결과들의 요약을 하나의 문자열로 합쳐 반환합니다.
     * LLM에게 컨텍스트로 넘겨 최신 정보를 반영한 답변을 만들도록 하는 용도입니다.
     */
    @SuppressWarnings("unchecked")
    public String search(String query) {
        Map<String, Object> requestBody = Map.of(
                "api_key", apiKey,
                "query", query,
                "max_results", 3,
                "search_depth", "basic"
        );

        Map<String, Object> response = restClient.post()
                .uri("/search")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("results")) {
            return "";
        }

        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");

        return results.stream()
                .map(r -> {
                    String title = String.valueOf(r.getOrDefault("title", ""));
                    String content = String.valueOf(r.getOrDefault("content", ""));
                    // 너무 긴 스니펫은 잘라서 프롬프트 부담을 줄임
                    if (content.length() > 300) {
                        content = content.substring(0, 300) + "...";
                    }
                    return "- " + title + ": " + content;
                })
                .collect(Collectors.joining("\n"));
    }
}
