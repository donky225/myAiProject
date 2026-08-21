package com.ai.llm.ingestion;

import com.ai.llm.opensearch.dto.RagDocument;
import com.ai.llm.ollama.OllamaService;
import com.ai.llm.opensearch.OpenSearchService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIngestionService {

    private final TextExtractor textExtractor;
    private final TextChunker textChunker;
    private final OllamaService ollamaService;
    private final OpenSearchService openSearchService;

    public DocumentIngestionService(
            TextExtractor textExtractor,
            TextChunker textChunker,
            OllamaService ollamaService,
            OpenSearchService openSearchService
    ) {
        this.textExtractor = textExtractor;
        this.textChunker = textChunker;
        this.ollamaService = ollamaService;
        this.openSearchService = openSearchService;
    }

    /**
     * 파일을 업로드받아 청킹 → 임베딩 → OpenSearch 저장까지 처리합니다.
     * @return 저장된 청크 개수
     */
    public int ingest(MultipartFile file) throws IOException {
        String rawText = textExtractor.extract(file);
        List<String> chunks = textChunker.chunk(rawText);

        String title = file.getOriginalFilename();
        int savedCount = 0;

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            float[] vector = ollamaService.embed(chunkText);

            String docId = UUID.nameUUIDFromBytes((title + "-" + i).getBytes()).toString();
            RagDocument document = new RagDocument(docId, title + " (청크 " + (i + 1) + "/" + chunks.size() + ")", chunkText, vector);

            openSearchService.saveDocument(document);
            savedCount++;
        }

        return savedCount;
    }
}