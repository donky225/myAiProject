package com.ai.llm.rag;

import com.ai.llm.pgvector.PgVectorRagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagEvaluationController {

    private final RagService ragService;
    private final PgVectorRagService pgVectorRagService;

    public RagEvaluationController(RagService ragService, PgVectorRagService pgVectorRagService) {
        this.ragService = ragService;
        this.pgVectorRagService = pgVectorRagService;
    }

    // RAGAS 평가를 위해 답변뿐 아니라 검색된 컨텍스트, 응답시간까지 JSON으로 반환합니다.
    @GetMapping("/api/rag/evaluate")
    public RagAnswer evaluate(@RequestParam String question,
                               @RequestParam(defaultValue = "opensearch") String store) {
        return "pgvector".equalsIgnoreCase(store)
                ? pgVectorRagService.askWithContext(question)
                : ragService.askWithContext(question);
    }
}
