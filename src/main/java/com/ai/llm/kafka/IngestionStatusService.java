package com.ai.llm.kafka;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IngestionStatusService {

    public enum Status { QUEUED, PROCESSING, DONE, FAILED }

    public record JobStatus(Status status, String message, Integer chunksIngested) {}

    private final Map<String, JobStatus> jobs = new ConcurrentHashMap<>();

    public void markQueued(String jobId) {
        jobs.put(jobId, new JobStatus(Status.QUEUED, "대기 중", null));
    }

    public void markProcessing(String jobId) {
        jobs.put(jobId, new JobStatus(Status.PROCESSING, "처리 중", null));
    }

    public void markDone(String jobId, int chunksIngested) {
        jobs.put(jobId, new JobStatus(Status.DONE, "완료", chunksIngested));
    }

    public void markFailed(String jobId, String errorMessage) {
        jobs.put(jobId, new JobStatus(Status.FAILED, errorMessage, null));
    }

    public JobStatus getStatus(String jobId) {
        return jobs.getOrDefault(jobId, new JobStatus(Status.FAILED, "존재하지 않는 작업 ID", null));
    }
}
