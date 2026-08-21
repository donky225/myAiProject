package com.ai.llm.opensearch;

import com.ai.llm.opensearch.dto.KnnSearchRequest;
import com.ai.llm.opensearch.dto.KnnSearchResponse;
import com.ai.llm.opensearch.dto.RagDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class OpenSearchService {

    private final RestClient restClient;
    private final String indexName;

    public OpenSearchService(
            @Value("${opensearch.base-url}") String baseUrl,
            @Value("${opensearch.index-name}") String indexName
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.indexName = indexName;
    }

    /**
     * 문서를 저장(또는 갱신)합니다.
     * PUT /{index}/_doc/{id}
     */
    public void saveDocument(RagDocument document) {
        restClient.put()
                .uri("/{index}/_doc/{id}", indexName, document.id())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                .body(document)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * 질문 벡터와 가장 유사한 문서 k개를 검색합니다.
     * POST /{index}/_search
     */
    public List<KnnSearchResponse.Hit> search(float[] queryVector, int k) {
        KnnSearchRequest request = KnnSearchRequest.of(queryVector, k);

        KnnSearchResponse response = restClient.post()
                .uri("/{index}/_search", indexName)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
                .body(request)
                .retrieve()
                .body(KnnSearchResponse.class);

        if (response == null || response.hits() == null) {
            return List.of();
        }
        return response.hits().hits();
    }
}