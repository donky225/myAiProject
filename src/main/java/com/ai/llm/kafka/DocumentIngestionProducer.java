package com.ai.llm.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class DocumentIngestionProducer {

    private static final String TOPIC = "document-ingestion-requested";

    private final KafkaTemplate<String, DocumentIngestionEvent> kafkaTemplate;
    private final IngestionStatusService statusService;

    public DocumentIngestionProducer(KafkaTemplate<String, DocumentIngestionEvent> kafkaTemplate,
                                      IngestionStatusService statusService) {
        this.kafkaTemplate = kafkaTemplate;
        this.statusService = statusService;
    }

    /**
     * 업로드된 파일을 임시 디스크 경로에 저장하고, Kafka에 처리 요청 이벤트를 발행합니다.
     * 실제 OCR/청킹/임베딩 처리는 이 메서드를 호출한 스레드가 아니라,
     * 별도의 Kafka Consumer가 백그라운드에서 수행합니다.
     *
     * @return 생성된 작업(job) ID. 클라이언트는 이 ID로 진행 상황을 조회합니다.
     */
    public String submitForIngestion(MultipartFile file, String store) throws IOException {
        String jobId = UUID.randomUUID().toString();

        Path tempFile = Files.createTempFile("ingest-" + jobId + "-", "-" + file.getOriginalFilename());
        file.transferTo(tempFile.toFile());

        DocumentIngestionEvent event = new DocumentIngestionEvent(
                jobId, tempFile.toString(), file.getOriginalFilename(), store
        );

        statusService.markQueued(jobId);
        kafkaTemplate.send(TOPIC, jobId, event);

        return jobId;
    }
}
