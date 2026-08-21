package com.ai.llm.ingestion;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
public class DocumentIngestionController {

    private final DocumentIngestionService ingestionService;

    public DocumentIngestionController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/api/documents/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        int savedCount = ingestionService.ingest(file);
        return Map.of(
                "filename", file.getOriginalFilename(),
                "chunksSaved", savedCount
        );
    }
}