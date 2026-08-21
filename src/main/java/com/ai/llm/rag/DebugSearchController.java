package com.ai.llm.rag;

import com.ai.llm.opensearch.OpenSearchService;
import com.ai.llm.opensearch.dto.KnnSearchResponse;
import com.ai.llm.ollama.OllamaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class DebugSearchController {

    private final OllamaService ollamaService;
    private final OpenSearchService openSearchService;

    public DebugSearchController(OllamaService ollamaService, OpenSearchService openSearchService) {
        this.ollamaService = ollamaService;
        this.openSearchService = openSearchService;
    }

    @GetMapping("/api/debug/search")
    public List<KnnSearchResponse.Hit> search(@RequestParam String question) {
        float[] queryVector = ollamaService.embed(question);
        return openSearchService.search(queryVector, 3);
    }
}