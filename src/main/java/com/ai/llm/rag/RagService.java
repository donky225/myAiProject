package com.ai.llm.rag;

import com.ai.llm.opensearch.dto.KnnSearchResponse;
import com.ai.llm.ollama.OllamaService;
import com.ai.llm.opensearch.OpenSearchService;
import com.ai.llm.rerank.RerankService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RagService {

    // 벡터 검색(코사인) 자체의 최소 관련성 컷오프. 리랭킹이 꺼져있거나 실패했을 때의 폴백 기준.
    // (기존 값 그대로 유지 — 문서 3개 기준 실측값 0.6~0.7대를 참고해 설정)
    private static final double COSINE_RELEVANCE_THRESHOLD = 0.55;

    // 리랭커(0~1 정규화 점수) 기준 관련성 컷오프.
    // TODO: bge-reranker-v2-m3 실측값을 몇 건 로깅해보고, COSINE_RELEVANCE_THRESHOLD를
    //       잡았던 것과 동일한 방식(실측 점수 분포 관찰)으로 캘리브레이션 필요.
    private static final double RERANK_RELEVANCE_THRESHOLD = 0.5;

    private final OllamaService ollamaService;
    private final OpenSearchService openSearchService;
    private final RerankService rerankService;
    private final int candidateCount;

    public RagService(
            OllamaService ollamaService,
            OpenSearchService openSearchService,
            RerankService rerankService,
            @Value("${rerank.candidate-count:10}") int candidateCount
    ) {
        this.ollamaService = ollamaService;
        this.openSearchService = openSearchService;
        this.rerankService = rerankService;
        this.candidateCount = candidateCount;
    }

    public String ask(String question) {
        List<KnnSearchResponse.Hit> relevantHits = getRelevantHits(question);
        return ollamaService.generate(buildSimplePrompt(question, relevantHits));
    }

    public RagAnswer askWithContext(String question) {
        long start = System.currentTimeMillis();

        List<KnnSearchResponse.Hit> relevantHits = getRelevantHits(question);
        List<String> contexts = relevantHits.stream()
                .map(hit -> hit._source().content())
                .toList();

        String answer = ollamaService.generate(buildStrictPrompt(question, relevantHits));
        long elapsed = System.currentTimeMillis() - start;

        return new RagAnswer(question, answer, contexts, elapsed);
    }

    /**
     * 벡터 검색(top-N 후보, N=candidateCount) → (활성화 시) cross-encoder 리랭킹으로 재정렬
     * → 관련성 임계값 필터링까지의 공통 파이프라인. ask()/askWithContext() 양쪽에서 재사용.
     *
     * 리랭크 서비스가 비활성화되었거나 호출에 실패하면, 기존 방식(코사인 유사도 상위 3건)으로
     * 자동 폴백합니다 — 리랭크 서비스 장애가 RAG 전체를 마비시키지 않도록 하기 위함입니다.
     */
    private List<KnnSearchResponse.Hit> getRelevantHits(String question) {
        float[] queryVector = ollamaService.embed(question);
        List<KnnSearchResponse.Hit> candidates = openSearchService.search(queryVector, candidateCount);

        if (rerankService.isEnabled()) {
            // NOTE: hit._id()가 KnnSearchResponse.Hit에 실제로 존재하는 필드인지 확인 필요.
            // 없다면 리스트 인덱스를 문자열 id로 써도 무방 (아래 대체 코드 참고).
            List<RerankService.RerankDoc> docs = candidates.stream()
                    .map(hit -> new RerankService.RerankDoc(
                            hit._id(),
                            hit._source().title() + " " + hit._source().content()
                    ))
                    .toList();

            List<RerankService.RerankedResult> reranked = rerankService.rerank(question, docs);

            if (!reranked.isEmpty()) {
                Map<String, KnnSearchResponse.Hit> byId = candidates.stream()
                        .collect(Collectors.toMap(KnnSearchResponse.Hit::_id, hit -> hit, (a, b) -> a));

                return reranked.stream()
                        .filter(r -> r.score() >= RERANK_RELEVANCE_THRESHOLD)
                        .map(r -> byId.get(r.id()))
                        .filter(Objects::nonNull)
                        .toList();
            }
            // 리랭크 서비스 호출 실패(빈 결과) → 아래 코사인 폴백으로 진행
        }

        return candidates.stream()
                .filter(hit -> hit._score() >= COSINE_RELEVANCE_THRESHOLD)
                .limit(3)
                .toList();
    }

    private String buildSimplePrompt(String question, List<KnnSearchResponse.Hit> relevantHits) {
        if (relevantHits.isEmpty()) {
            return "반드시 한국어로만 답변하세요. 영어를 사용하지 마세요.\n\n" + question;
        }
        String context = formatContext(relevantHits);
        return """
                반드시 한국어로만 답변하세요. 영어를 사용하지 마세요.
                다음 참고 문서를 활용할 수 있으면 활용해서 질문에 답변해줘. 참고 문서와 관련 없는 질문이면 너의 일반 지식으로 답변해도 돼.

                [참고 문서]
                %s

                [질문]
                %s
                """.formatted(context, question);
    }

    private String buildStrictPrompt(String question, List<KnnSearchResponse.Hit> relevantHits) {
        if (relevantHits.isEmpty()) {
            return "반드시 한국어로만 답변하세요. 영어를 사용하지 마세요.\n\n" + question;
        }
        String context = formatContext(relevantHits);
        return """
                당신은 회사 문서에 기반해서만 답변하는 어시스턴트입니다. 아래 규칙을 반드시 지키세요.

                1. 반드시 한국어로만 답변하세요. 영어를 사용하지 마세요.
                2. 반드시 [참고 문서]에 명시적으로 나온 내용만 근거로 답변하세요. 문서에 없는 숫자나 사실을 추측하거나 만들어내지 마세요.
                3. [참고 문서]에 질문에 대한 답이 없으면, 답을 지어내지 말고 "제공된 문서에서 해당 정보를 찾을 수 없습니다"라고 명확히 말하세요.
                4. 답변 마지막에 어느 문서 내용을 근거로 했는지 한 줄로 요약해 밝히세요. (예: "근거: 참고 문서의 임원 현황 표")
                5. [참고 문서]가 비어있거나 질문과 전혀 무관하면, 그 사실을 먼저 밝힌 뒤 일반 지식으로 답변해도 됩니다.

                [참고 문서]
                %s

                [질문]
                %s
                """.formatted(context, question);
    }

    private String formatContext(List<KnnSearchResponse.Hit> hits) {
        return hits.stream()
                .map(hit -> "- " + hit._source().title() + ": " + hit._source().content())
                .collect(Collectors.joining("\n"));
    }
}