package com.ai.llm.opensearch;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class OpenSearchIndexInitializer {

    private final RestClient restClient;
    private final String indexName;

    public OpenSearchIndexInitializer(
            @Value("${opensearch.base-url}") String baseUrl,
            @Value("${opensearch.index-name}") String indexName
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.indexName = indexName;
    }

    @PostConstruct
    public void createIndexIfNotExists() {
        try {
            restClient.head().uri("/{index}", indexName).retrieve().toBodilessEntity();
            // 이미 존재하면 아무것도 안 함
        } catch (HttpClientErrorException.NotFound e) {
            String mapping = """
                {
                  "settings": { "index": { "knn": true } },
                  "mappings": {
                    "properties": {
                      "id": { "type": "keyword" },
                      "title": { "type": "text" },
                      "content": { "type": "text" },
                      "embedding": {
                        "type": "knn_vector",
                        "dimension": 1024,
                        "method": { "name": "hnsw", "space_type": "cosinesimil", "engine": "lucene" }
                      }
                    }
                  }
                }
                """;

            restClient.put()
                    .uri("/{index}", indexName)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                    .body(mapping)
                    .retrieve()
                    .toBodilessEntity();
        }
    }
}