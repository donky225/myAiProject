package com.ai.llm.mcp;

import com.ai.llm.pgvector.PgVectorRagService;
import com.ai.llm.rag.RagAnswer;
import com.ai.llm.rag.RagService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기존 RAG 파이프라인(벡터검색 → 리랭킹 → LLM 생성)을 MCP 도구로 노출합니다.
 * Claude Desktop 같은 MCP 클라이언트가 이 프로젝트의 문서 검색 기능을
 * "도구"로 호출할 수 있게 되며, RagService/PgVectorRagService의 리랭킹·캐싱·
 * 폴백 로직을 그대로 재사용합니다 (MCP 계층은 얇은 어댑터 역할만 합니다).
 */
@Component
public class RagMcpTools {

    private final RagService ragService;
    private final PgVectorRagService pgVectorRagService;

    public RagMcpTools(RagService ragService, PgVectorRagService pgVectorRagService) {
        this.ragService = ragService;
        this.pgVectorRagService = pgVectorRagService;
    }

    public record RagToolResult(String answer, List<String> sources, long elapsedMillis) {}

    @McpTool(
            name = "search_company_documents",
            description = "업로드된 회사 문서(사규, 보고서 등)를 벡터 검색 + 리랭킹으로 조회해 질문에 답변합니다. " +
                    "일반 지식이 아닌, 업로드된 문서에 근거한 답변이 필요할 때 사용하세요."
    )
    public RagToolResult searchCompanyDocuments(
            @McpToolParam(description = "문서에서 답을 찾고자 하는 질문 (한국어 또는 영어)", required = true)
                    String question,
            @McpToolParam(description = "검색할 벡터스토어: 'opensearch' 또는 'pgvector' (기본값 opensearch)", required = false)
                    String store
    ) {
        String selectedStore = (store == null || store.isBlank()) ? "opensearch" : store;

        RagAnswer answer = "pgvector".equalsIgnoreCase(selectedStore)
                ? pgVectorRagService.askWithContext(question)
                : ragService.askWithContext(question);

        return new RagToolResult(answer.answer(), answer.contexts(), answer.elapsedMillis());
    }
}