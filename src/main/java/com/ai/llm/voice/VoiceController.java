package com.ai.llm.voice;

import com.ai.llm.pgvector.PdfTextExtractionService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final VoiceService voiceService;
    private final PdfTextExtractionService pdfTextExtractionService;

    public VoiceController(VoiceService voiceService,
                            PdfTextExtractionService pdfTextExtractionService) {
        this.voiceService = voiceService;
        this.pdfTextExtractionService = pdfTextExtractionService;
    }

    /**
     * 음성 파일(m4a/mp3/wav)을 업로드하면 텍스트로 전사해서 반환합니다.
     */
    @PostMapping("/stt")
    public Map<String, Object> speechToText(@RequestParam("file") MultipartFile file) throws IOException {
        return voiceService.speechToText(file);
    }

    /**
     * 텍스트를 직접 입력하거나 PDF/TXT 파일을 업로드하면, 그 내용을 음성(WAV)으로 변환해 반환합니다.
     * text 파라미터가 있으면 그걸 우선 사용하고, 없으면 file(PDF/TXT)에서 텍스트를 추출합니다.
     */
    @PostMapping(value = "/tts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> textToSpeech(
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) throws IOException {

        String content = resolveText(text, file);

        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // TTS 엔진의 안정성을 위해 너무 긴 텍스트는 앞부분만 사용 (필요시 조정)
        String truncated = content.length() > 1000 ? content.substring(0, 1000) : content;

        byte[] audio = voiceService.textToSpeech(truncated);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("audio/wav"));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename("speech.wav").build()
        );

        return ResponseEntity.ok().headers(headers).body(audio);
    }

    private String resolveText(String text, MultipartFile file) throws IOException {
        if (text != null && !text.isBlank()) {
            return text;
        }

        if (file == null || file.isEmpty()) {
            return null;
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();

        if (filename.endsWith(".pdf")) {
            return pdfTextExtractionService.extractText(file);
        } else if (filename.endsWith(".txt")) {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }

        throw new IllegalArgumentException("지원하지 않는 파일 형식입니다 (.pdf, .txt만 가능)");
    }
}
