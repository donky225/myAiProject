package com.ai.llm.websearch;

import com.ai.llm.cache.CacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WebSearchService {

    // 같은 검색어가 짧은 시간 안에 반복되면(예: "오늘 날씨") API를 다시 쓰지 않고 캐시를 반환합니다.
    // Tavily 무료 티어(월 1,000회) 절약 + 응답 즉시 반환 효과가 있습니다.
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    private final RestClient restClient;
    private final String apiKey;
    private final CacheService cacheService;

    public WebSearchService(@Value("${tavily.api-key}") String apiKey, CacheService cacheService) {
        this.apiKey = apiKey;
        this.cacheService = cacheService;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.tavily.com")
                .build();
    }

    @SuppressWarnings("unchecked")
    public String search(String query) {
        String cacheKey = "search:" + query.trim().toLowerCase();

        String cached = cacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }

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

        String formatted = results.stream()
                .map(r -> {
                    String title = String.valueOf(r.getOrDefault("title", ""));
                    String content = String.valueOf(r.getOrDefault("content", ""));
                    if (content.length() > 300) {
                        content = content.substring(0, 300) + "...";
                    }
                    return "- " + title + ": " + content;
                })
                .collect(Collectors.joining("\n"));

        cacheService.put(cacheKey, formatted, CACHE_TTL);
        return formatted;
    }
}