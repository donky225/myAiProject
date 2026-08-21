package com.ai.llm.rag;

import com.ai.llm.pgvector.PgVectorRagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagController {

    private final RagService ragService;
    private final PgVectorRagService pgVectorRagService;

    public RagController(RagService ragService, PgVectorRagService pgVectorRagService) {
        this.ragService = ragService;
        this.pgVectorRagService = pgVectorRagService;
    }

    // store=opensearch(기본값) 또는 store=pgvector 로 두 벡터스토어를 비교 테스트할 수 있습니다.
    @GetMapping("/api/rag/ask")
    public String ask(@RequestParam String question,
                      @RequestParam(defaultValue = "opensearch") String store) {
        return "pgvector".equalsIgnoreCase(store)
                ? pgVectorRagService.ask(question)
                : ragService.ask(question);
    }
}