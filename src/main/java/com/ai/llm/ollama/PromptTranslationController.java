package com.ai.llm.ollama;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PromptTranslationController {

    private final OllamaService ollamaService;

    public PromptTranslationController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    // 한글 이미지 생성 프롬프트를 Stable Diffusion이 이해하는 영어 프롬프트로 번역합니다.
    @PostMapping("/api/image/translate-prompt")
    public Map<String, String> translatePrompt(@RequestBody Map<String, String> body) {
        String koreanPrompt = body.get("prompt");

        String instruction = """
                다음 한국어 문장을 Stable Diffusion 이미지 생성 프롬프트에 적합한 영어로 번역해줘.
                번역 결과만 출력하고, 설명이나 따옴표는 붙이지 마.
                가능하면 구체적인 시각적 묘사(스타일, 색감, 구도 등)를 살려서 번역해줘.

                한국어: %s
                영어:
                """.formatted(koreanPrompt);

        String translated = ollamaService.generate(instruction).trim();

        return Map.of("original", koreanPrompt, "translated", translated);
    }
}
