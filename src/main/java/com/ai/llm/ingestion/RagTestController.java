package com.ai.llm.ingestion;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rag")
public class RagTestController {

    private final OpenSearchIngestService ingestService;

    public RagTestController(
            OpenSearchIngestService ingestService) {

        this.ingestService = ingestService;
    }

    /**
     * RAG ingestion 테스트
     *
     * 호출 예:
     *
     * POST /api/rag/test?title=rag-test
     *
     * Body:
     * 테스트할 원문
     */
    @PostMapping("/test")
    public Map<String, Object> test(
            @RequestParam(defaultValue = "rag-test") String title,
            @RequestBody String text) {

        int count = ingestService.ingest(
                title,
                text
        );

        return Map.of(
                "success", true,
                "title", title,
                "chunkCount", count
        );
    }
}