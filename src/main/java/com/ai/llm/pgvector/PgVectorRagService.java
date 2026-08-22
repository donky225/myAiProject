package com.ai.llm.pgvector;

import com.ai.llm.ollama.OllamaService;
import com.ai.llm.rag.RagAnswer;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PgVectorRagService {

    // 기존 RagService(OpenSearch)와 같은 임계값을 사용해 두 스토어 결과를 공정하게 비교합니다.
    private static final double RELEVANCE_THRESHOLD = 0.25;

    private final OllamaService ollamaService;
    private final PgVectorService pgVectorService;

    public PgVectorRagService(OllamaService ollamaService, PgVectorService pgVectorService) {
        this.ollamaService = ollamaService;
        this.pgVectorService = pgVectorService;
    }

    public String ask(String question) {
        List<Document> hits = pgVectorService.search(question, 5);

        List<Document> relevantHits = hits.stream()
                .filter(doc -> doc.getScore() != null && doc.getScore() >= RELEVANCE_THRESHOLD)
                .toList();

        String prompt;
        if (relevantHits.isEmpty()) {
            prompt = question;
        } else {
            String context = relevantHits.stream()
                    .map(doc -> "- " + doc.getText())
                    .collect(Collectors.joining("\n"));

            prompt = """
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

        return ollamaService.generate(prompt);
    }

    public RagAnswer askWithContext(String question) {
        long start = System.currentTimeMillis();

        List<Document> hits = pgVectorService.search(question, 3);

        List<Document> relevantHits = hits.stream()
                .filter(doc -> doc.getScore() != null && doc.getScore() >= RELEVANCE_THRESHOLD)
                .toList();

        List<String> contexts = relevantHits.stream()
                .map(Document::getText)
                .toList();

        String prompt;
        if (relevantHits.isEmpty()) {
            prompt = question;
        } else {
            String context = relevantHits.stream()
                    .map(doc -> "- " + doc.getText())
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
