package com.ai.llm.pgvector;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PgVectorIngestService {

    // 청크 크기와 겹침 구간. 문서 성격에 맞춰 조정 가능합니다.
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;

    private final PgVectorService pgVectorService;

    public PgVectorIngestService(PgVectorService pgVectorService) {
        this.pgVectorService = pgVectorService;
    }

    public int ingestPdf(MultipartFile file) throws IOException {
        String text = extractText(file);
        List<Document> chunks = chunk(text, file.getOriginalFilename());
        pgVectorService.add(chunks);
        return chunks.size();
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private List<Document> chunk(String text, String sourceName) {
        List<Document> documents = new ArrayList<>();
        String normalized = text.replaceAll("\\s+", " ").trim();

        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            String content = normalized.substring(start, end);

            Document doc = new Document(
                    content,
                    Map.of(
                            "source", sourceName == null ? "unknown" : sourceName,
                            "chunkIndex", index
                    )
            );
            documents.add(doc);

            index++;
            start += (CHUNK_SIZE - CHUNK_OVERLAP);
        }

        return documents;
    }
}
