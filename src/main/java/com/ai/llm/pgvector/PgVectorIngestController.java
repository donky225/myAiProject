package com.ai.llm.pgvector;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
public class PgVectorIngestController {

    private final PgVectorIngestService pgVectorIngestService;

    public PgVectorIngestController(PgVectorIngestService pgVectorIngestService) {
        this.pgVectorIngestService = pgVectorIngestService;
    }

    @PostMapping("/api/pgvector/ingest")
    public Map<String, Object> ingest(@RequestParam("file") MultipartFile file) throws IOException {
        int chunkCount = pgVectorIngestService.ingestPdf(file);
        return Map.of(
                "filename", file.getOriginalFilename(),
                "chunksIngested", chunkCount
        );
    }
}
