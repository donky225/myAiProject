package com.ai.llm.kafka;

import org.springframework.lang.NonNull;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Kafka Consumer가 디스크에 저장된 임시 파일을 읽어서, 기존의
 * PgVectorIngestService.ingestPdf(MultipartFile) 등을 그대로 재사용할 수 있도록
 * MultipartFile 인터페이스로 감싸는 어댑터입니다.
 */
public class ByteArrayMultipartFile implements MultipartFile {

    private final byte[] content;
    private final String filename;

    public ByteArrayMultipartFile(Path filePath, String originalFilename) throws IOException {
        this.content = Files.readAllBytes(filePath);
        this.filename = originalFilename;
    }

    @Override
    @NonNull
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return filename;
    }

    @Override
    public String getContentType() {
        return "application/octet-stream";
    }

    @Override
    public boolean isEmpty() {
        return content.length == 0;
    }

    @Override
    public long getSize() {
        return content.length;
    }

    @Override
    @NonNull
    public byte[] getBytes() {
        return content;
    }

    @Override
    @NonNull
    public InputStream getInputStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
        try (FileOutputStream out = new FileOutputStream(dest)) {
            out.write(content);
        }
    }
}
