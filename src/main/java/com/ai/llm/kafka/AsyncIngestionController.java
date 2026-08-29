package com.ai.llm.kafka;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/documents/async")
public class AsyncIngestionController {

    private final DocumentIngestionProducer producer;
    private final IngestionStatusService statusService;

    public AsyncIngestionController(DocumentIngestionProducer producer,
                                     IngestionStatusService statusService) {
        this.producer = producer;
        this.statusService = statusService;
    }

    /**
     * 파일을 즉시 처리하지 않고, Kafka에 처리 요청만 발행한 뒤 바로 응답합니다.
     * 실제 OCR/청킹/임베딩은 DocumentIngestionConsumer가 백그라운드에서 처리합니다.
     */
    @PostMapping("/upload")
    public Map<String, String> uploadAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "store", defaultValue = "pgvector") String store
    ) throws IOException {
        String jobId = producer.submitForIngestion(file, store);
        return Map.of("jobId", jobId, "status", "QUEUED");
    }

    @GetMapping("/status/{jobId}")
    public IngestionStatusService.JobStatus getStatus(@PathVariable String jobId) {
        return statusService.getStatus(jobId);
    }
}
