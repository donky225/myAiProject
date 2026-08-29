package com.ai.llm.kafka;

import com.ai.llm.pgvector.PgVectorIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DocumentIngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionConsumer.class);

    private final PgVectorIngestService pgVectorIngestService;
    private final IngestionStatusService statusService;

    public DocumentIngestionConsumer(PgVectorIngestService pgVectorIngestService,
                                      IngestionStatusService statusService) {
        this.pgVectorIngestService = pgVectorIngestService;
        this.statusService = statusService;
    }

    @KafkaListener(topics = "document-ingestion-requested", groupId = "document-ingestion-group")
    public void handle(DocumentIngestionEvent event) {
        log.info("[Kafka] 인제스트 요청 수신: jobId={}, file={}, store={}",
                event.jobId(), event.originalFilename(), event.store());

        statusService.markProcessing(event.jobId());
        Path tempFile = Path.of(event.tempFilePath());

        try {
            ByteArrayMultipartFile multipartFile = new ByteArrayMultipartFile(tempFile, event.originalFilename());

            int chunksIngested;
            if ("pgvector".equalsIgnoreCase(event.store())) {
                chunksIngested = pgVectorIngestService.ingestPdf(multipartFile);
            } else {
                // TODO: OpenSearch 인제스트 서비스 메서드가 준비되면 여기에 연결하세요.
                // 예: chunksIngested = openSearchIngestService.ingestPdf(multipartFile);
                throw new UnsupportedOperationException(
                        "store=" + event.store() + " 경로는 아직 비동기 파이프라인에 연결되지 않았습니다."
                );
            }

            statusService.markDone(event.jobId(), chunksIngested);
            log.info("[Kafka] 인제스트 완료: jobId={}, {}청크", event.jobId(), chunksIngested);

        } catch (Exception e) {
            log.error("[Kafka] 인제스트 실패: jobId={}", event.jobId(), e);
            statusService.markFailed(event.jobId(), e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception e) {
                log.warn("[Kafka] 임시 파일 삭제 실패: {}", tempFile, e);
            }
        }
    }
}
