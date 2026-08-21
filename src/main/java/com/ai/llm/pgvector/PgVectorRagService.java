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
    private static final double RELEVANCE_THRESHOLD = 0.55;

    private final OllamaService ollamaService;
    private final PgVectorService pgVectorService;

    public PgVectorRagService(OllamaService ollamaService, PgVectorService pgVectorService) {
        this.ollamaService = ollamaService;
        this.pgVectorService = pgVectorService;
    }

    public String ask(String question) {
        List<Document> hits = pgVectorService.search(question, 3);

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
