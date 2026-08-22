package com.ai.llm.voice;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class VoiceService {

    private static final String VOICE_SERVICE_BASE_URL = "http://localhost:8001";

    private final RestClient restClient;

    public VoiceService() {
        this.restClient = RestClient.builder()
                .baseUrl(VOICE_SERVICE_BASE_URL)
                .build();
    }

    /**
     * 음성 파일을 Python STT 서버로 보내 텍스트로 전사합니다.
     */
    public Map<String, Object> speechToText(MultipartFile file) throws IOException {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });

        return restClient.post()
                .uri("/stt")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve()
                .body(Map.class);
    }

    /**
     * 텍스트를 Python TTS 서버로 보내 음성(WAV) 바이트를 받아옵니다.
     */
    public byte[] textToSpeech(String text) {
        return restClient.post()
                .uri("/tts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text, "speed", 1.0))
                .retrieve()
                .body(byte[].class);
    }
}
