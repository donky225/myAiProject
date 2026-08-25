package com.ai.llm.opensearch;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class OpenSearchIndexInitializer {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchIndexInitializer.class);

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
        // OpenSearch가 이 배포 환경에 없을 수도 있습니다(예: 경량화된 클라우드 데모 서버).
        // 연결 실패가 애플리케이션 전체 기동을 막지 않도록, 여기서 예외를 완전히 흡수합니다.
        try {
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

                log.info("OpenSearch 인덱스 '{}' 생성 완료", indexName);
            }
        } catch (Exception e) {
            log.warn("OpenSearch({})에 연결할 수 없어 인덱스 초기화를 건너뜁니다. " +
                            "이 환경에서는 OpenSearch 없이 pgvector 경로만 사용 가능합니다. 원인: {}",
                    indexName, e.getMessage());
        }
    }
}