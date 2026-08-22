package com.ai.llm.ingestion;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class OllamaEmbeddingService {

    private final RestClient restClient;

    public OllamaEmbeddingService() {

        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:11434")
                .build();
    }


    /**
     * Ollama Embedding
     */
    public float[] embed(String text) {

        Map<String, Object> request = Map.of(
                "model", "qwen3-embedding:0.6b",
                "input", text
        );


        Map response = restClient.post()
                .uri("/api/embed")
                .body(request)
                .retrieve()
                .body(Map.class);


        if (response == null) {
            throw new RuntimeException(
                    "Ollama embedding response가 없습니다."
            );
        }


        /*
         * Ollama /api/embed 응답:
         *
         * {
         *   "embeddings": [
         *      [...]
         *   ]
         * }
         */


        List<List<Number>> embeddings =
                (List<List<Number>>) response.get("embeddings");


        if (embeddings == null || embeddings.isEmpty()) {

            throw new RuntimeException(
                    "embedding 결과가 없습니다."
            );
        }


        List<Number> vector =
                embeddings.get(0);


        float[] result =
                new float[vector.size()];


        for (int i = 0; i < vector.size(); i++) {

            result[i] =
                    vector.get(i).floatValue();
        }


        return result;
    }
}