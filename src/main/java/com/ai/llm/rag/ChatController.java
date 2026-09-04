package com.ai.llm.rag;

import com.ai.llm.ollama.OllamaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final OllamaService ollamaService;

    public ChatController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    /** RAG 검색 없이 Qwen3 4B 자체 지식으로만 답변 */
    @GetMapping("/api/chat/ask")
    public String ask(@RequestParam String question) {
        return ollamaService.generate(question);
    }
}
