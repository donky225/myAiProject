package com.ai.llm.pgvector;

import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// 임시 진단용: 관련성 임계값 필터링 없이 pgvector 검색 원본 결과(점수 포함)를 그대로 반환합니다.
// 원인 파악 후 삭제해도 됩니다.
@RestController
public class PgVectorDebugController {

    private final PgVectorService pgVectorService;

    public PgVectorDebugController(PgVectorService pgVectorService) {
        this.pgVectorService = pgVectorService;
    }

    @GetMapping("/api/pgvector/debug-search")
    public List<Map<String, Object>> debugSearch(@RequestParam String question) {
        List<Document> hits = pgVectorService.search(question, 5);
        return hits.stream()
                .map(doc -> Map.<String, Object>of(
                        "score", doc.getScore() == null ? "null" : doc.getScore(),
                        "textPreview", doc.getText() != null && doc.getText().length() > 80
                                ? doc.getText().substring(0, 80) + "..."
                                : String.valueOf(doc.getText())
                ))
                .toList();
    }
}
