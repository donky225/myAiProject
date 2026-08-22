package com.ai.llm.ingestion;

import com.ai.llm.opensearch.OpenSearchService;
import com.ai.llm.opensearch.dto.RagDocument;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OpenSearchIngestService {

    private final TextChunker textChunker;
    private final OllamaEmbeddingService embeddingService;
    private final OpenSearchService openSearchService;

    public OpenSearchIngestService(
            TextChunker textChunker,
            OllamaEmbeddingService embeddingService,
            OpenSearchService openSearchService) {

        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.openSearchService = openSearchService;
    }


    /**
     * 텍스트를 RAG 문서로 저장
     *
     * 처리 순서:
     *
     * 원본 Text
     *     ↓
     * TextChunker
     *     ↓
     * 문장 단위 Chunk
     *     ↓
     * Ollama Embedding
     *     ↓
     * RagDocument
     *     ↓
     * OpenSearch
     */
    public int ingest(String title, String text) {

        System.out.println();
        System.out.println("============================================================");
        System.out.println("RAG INGESTION START");
        System.out.println("============================================================");

        System.out.println("title : " + title);
        System.out.println("원본 길이 : " + (text == null ? 0 : text.length()));


        if (text == null || text.isBlank()) {

            System.out.println("원본 Text가 비어 있습니다.");

            return 0;
        }


        /*
         * --------------------------------------------------------
         * 1. Chunk
         * --------------------------------------------------------
         */

        List<String> chunks = textChunker.chunk(text);


        System.out.println();
        System.out.println("============================================================");
        System.out.println("CHUNK RESULT");
        System.out.println("============================================================");

        System.out.println("총 Chunk 개수 : " + chunks.size());


        /*
         * --------------------------------------------------------
         * 2. 각 Chunk 처리
         * --------------------------------------------------------
         */

        for (int i = 0; i < chunks.size(); i++) {

            String chunk = chunks.get(i);


            System.out.println();
            System.out.println("------------------------------------------------------------");
            System.out.println("CHUNK [" + i + "]");
            System.out.println("길이 : " + chunk.length());
            System.out.println("------------------------------------------------------------");

            System.out.println(chunk);


            /*
             * ----------------------------------------------------
             * Embedding
             * ----------------------------------------------------
             */

            float[] embedding =
                    embeddingService.embed(chunk);


            System.out.println();
            System.out.println("[EMBEDDING]");
            System.out.println("model     : qwen3-embedding:0.6b");
            System.out.println("dimension : " + embedding.length);


            /*
             * ----------------------------------------------------
             * RagDocument 생성
             * ----------------------------------------------------
             */

            String id = UUID.randomUUID().toString();


            RagDocument document =
                    new RagDocument(
                            id,
                            title,
                            chunk,
                            embedding
                    );


            /*
             * ----------------------------------------------------
             * OpenSearch 저장
             * ----------------------------------------------------
             */

            openSearchService.saveDocument(document);


            System.out.println();
            System.out.println("[OPENSEARCH SAVE]");
            System.out.println("index       : rag_documents");
            System.out.println("id          : " + id);
            System.out.println("title       : " + title);
            System.out.println("chunkIndex  : " + i);
            System.out.println("contentSize : " + chunk.length());
            System.out.println("vectorSize  : " + embedding.length);
        }


        System.out.println();
        System.out.println("============================================================");
        System.out.println("RAG INGESTION COMPLETE");
        System.out.println("============================================================");

        System.out.println("title       : " + title);
        System.out.println("chunk count : " + chunks.size());


        return chunks.size();
    }
}