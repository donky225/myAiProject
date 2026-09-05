package com.ai.llm.pgvector;

import com.ai.llm.ollama.OllamaService;
import com.ai.llm.rag.RagAnswer;
import com.ai.llm.rerank.RerankService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PgVectorRagService {

    // 기존 RagService(OpenSearch)와 다른 스케일이라 별도 값 유지 (pgvector 코사인 분포 실측 기준).
    // 리랭킹이 꺼져있거나 실패했을 때의 폴백 기준.
    private static final double RELEVANCE_THRESHOLD = 0.25;

    // 리랭커(0~1 정규화 점수) 기준 관련성 컷오프. OpenSearch 쪽과 동일한 스케일이므로 같은 값 사용.
    // TODO: 실측 후 필요시 조정.
    private static final double RERANK_RELEVANCE_THRESHOLD = 0.5;

    // 리랭킹 없이 폴백할 때, 기존 ask()/askWithContext()가 각각 다르게 쓰던 top-k를 그대로 보존.
    private static final int ASK_FALLBACK_LIMIT = 5;
    private static final int ASK_WITH_CONTEXT_FALLBACK_LIMIT = 3;

    private final OllamaService ollamaService;
    private final PgVectorService pgVectorService;
    private final RerankService rerankService;
    private final int candidateCount;

    public PgVectorRagService(
            OllamaService ollamaService,
            PgVectorService pgVectorService,
            RerankService rerankService,
            @Value("${rerank.candidate-count:10}") int candidateCount
    ) {
        this.ollamaService = ollamaService;
        this.pgVectorService = pgVectorService;
        this.rerankService = rerankService;
        this.candidateCount = candidateCount;
    }

    public String ask(String question) {
        List<Document> relevantHits = getRelevantHits(question, ASK_FALLBACK_LIMIT);
        return ollamaService.generate(buildStrictPrompt(question, relevantHits));
    }

    public RagAnswer askWithContext(String question) {
        long start = System.currentTimeMillis();

        List<Document> relevantHits = getRelevantHits(question, ASK_WITH_CONTEXT_FALLBACK_LIMIT);
        List<String> contexts = relevantHits.stream()
                .map(Document::getText)
                .toList();

        String answer = ollamaService.generate(buildSimplePrompt(question, relevantHits));
        long elapsed = System.currentTimeMillis() - start;

        return new RagAnswer(question, answer, contexts, elapsed);
    }

    /**
     * 벡터 검색(top-N 후보, N=candidateCount) → (활성화 시) cross-encoder 리랭킹으로 재정렬
     * → 관련성 임계값 필터링까지의 공통 파이프라인.
     *
     * 리랭크 서비스가 비활성화되었거나 호출에 실패하면, 기존 방식(코사인 유사도 기준,
     * fallbackLimit 만큼)으로 자동 폴백합니다.
     *
     * @param fallbackLimit 리랭킹 미적용 시 최대 문서 개수 (기존 ask()=5, askWithContext()=3 유지)
     */
    private List<Document> getRelevantHits(String question, int fallbackLimit) {
        List<Document> candidates = pgVectorService.search(question, candidateCount);

        if (rerankService.isEnabled()) {
            // NOTE: Document.getId()가 Spring AI Document API에 실제로 존재하는지 확인 필요.
            // (Spring AI 표준 Document는 getId()/getText()/getMetadata()/getScore() 제공)
            List<RerankService.RerankDoc> docs = candidates.stream()
                    .map(doc -> new RerankService.RerankDoc(doc.getId(), doc.getText()))
                    .toList();

            List<RerankService.RerankedResult> reranked = rerankService.rerank(question, docs);

            if (!reranked.isEmpty()) {
                Map<String, Document> byId = candidates.stream()
                        .collect(Collectors.toMap(Document::getId, doc -> doc, (a, b) -> a));

                return reranked.stream()
                        .filter(r -> r.score() >= RERANK_RELEVANCE_THRESHOLD)
                        .map(r -> byId.get(r.id()))
                        .filter(Objects::nonNull)
                        .toList();
            }
            // 리랭크 서비스 호출 실패(빈 결과) → 아래 코사인 폴백으로 진행
        }

        return candidates.stream()
                .filter(doc -> doc.getScore() != null && doc.getScore() >= RELEVANCE_THRESHOLD)
                .limit(fallbackLimit)
                .toList();
    }

    private String buildStrictPrompt(String question, List<Document> relevantHits) {
        if (relevantHits.isEmpty()) {
            return question;
        }
        String context = formatContext(relevantHits);
        return """
                당신은 회사 문서에 기반해서만 답변하는 어시스턴트입니다. 아래 규칙을 반드시 지키세요.

                1. 반드시 [참고 문서]에 명시적으로 나온 내용만 근거로 답변하세요. 문서에 없는 숫자나 사실을 추측하거나 만들어내지 마세요.
                2. [참고 문서]에 질문에 대한 답이 없으면, 답을 지어내지 말고 "제공된 문서에서 해당 정보를 찾을 수 없습니다"라고 명확히 말하세요.
                3. 답변 마지막에 어느 문서 내용을 근거로 했는지 한 줄로 요약해 밝히세요. (예: "근거: 참고 문서의 임원 현황 표")
                4. [참고 문서]가 비어있거나 질문과 전혀 무관하면, 그 사실을 먼저 밝힌 뒤 일반 지식으로 답변해도 됩니다.

                [참고 문서]
                %s

                [질문]
                %s
                """.formatted(context, question);
    }

    private String buildSimplePrompt(String question, List<Document> relevantHits) {
        if (relevantHits.isEmpty()) {
            return question;
        }
        String context = formatContext(relevantHits);
        return """
                다음 참고 문서를 활용할 수 있으면 활용해서 질문에 답변해줘. 참고 문서와 관련 없는 질문이면 너의 일반 지식으로 답변해도 돼.

                [참고 문서]
                %s

                [질문]
                %s
                """.formatted(context, question);
    }

    private String formatContext(List<Document> hits) {
        return hits.stream()
                .map(doc -> "- " + doc.getText())
                .collect(Collectors.joining("\n"));
    }
}