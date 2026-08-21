package com.ai.llm.rag;

import com.ai.llm.opensearch.dto.KnnSearchResponse;
import com.ai.llm.ollama.OllamaService;
import com.ai.llm.opensearch.OpenSearchService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    // 이 점수 이상일 때만 "관련 문서"로 인정합니다. 문서 3개 기준 실측값(0.6~0.7대)을 참고해 임계값을 잡았습니다.
    private static final double RELEVANCE_THRESHOLD = 0.55;

    private final OllamaService ollamaService;
    private final OpenSearchService openSearchService;

    public RagService(OllamaService ollamaService, OpenSearchService openSearchService) {
        this.ollamaService = ollamaService;
        this.openSearchService = openSearchService;
    }

    public String ask(String question) {
        float[] queryVector = ollamaService.embed(question);
        List<KnnSearchResponse.Hit> hits = openSearchService.search(queryVector, 3);

        List<KnnSearchResponse.Hit> relevantHits = hits.stream()
                .filter(hit -> hit._score() >= RELEVANCE_THRESHOLD)
                .toList();

        String prompt;
        if (relevantHits.isEmpty()) {
            // 관련 문서가 없으면 컨텍스트 없이 LLM 자체 지식으로 답변
            prompt = question;
        } else {
            String context = relevantHits.stream()
                    .map(hit -> "- " + hit._source().title() + ": " + hit._source().content())
                    .collect(Collectors.joining("\n"));

            prompt = """
                    다음 참고 문서를 활용할 수 있으면 활용해서 질문에 답변해줘. 참고 문서와 관련 없는 질문이면 너의 일반 지식으로 답변해도 돼.

                    [참고 문서]
                    %s

                    [질문]
                    %s
                    """.formatted(context, question);
        }

        return ollamaService.generate(prompt);
    }

    public RagAnswer askWithContext(String question) {
        long start = System.currentTimeMillis();

        float[] queryVector = ollamaService.embed(question);
        List<KnnSearchResponse.Hit> hits = openSearchService.search(queryVector, 3);

        List<KnnSearchResponse.Hit> relevantHits = hits.stream()
                .filter(hit -> hit._score() >= RELEVANCE_THRESHOLD)
                .toList();

        List<String> contexts = relevantHits.stream()
                .map(hit -> hit._source().content())
                .toList();

        String prompt;
        if (relevantHits.isEmpty()) {
            prompt = question;
        } else {
            String context = relevantHits.stream()
                    .map(hit -> "- " + hit._source().title() + ": " + hit._source().content())
                    .collect(Collectors.joining("\n"));

            prompt = """
                다음 참고 문서를 활용할 수 있으면 활용해서 질문에 답변해줘. 참고 문서와 관련 없는 질문이면 너의 일반 지식으로 답변해도 돼.

                [참고 문서]
                %s

                [질문]
                %s
                """.formatted(context, question);
        }

        String answer = ollamaService.generate(prompt);
        long elapsed = System.currentTimeMillis() - start;

        return new RagAnswer(question, answer, contexts, elapsed);
    }

}