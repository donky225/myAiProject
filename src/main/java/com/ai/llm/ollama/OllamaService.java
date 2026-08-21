package com.ai.llm.ollama;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OllamaService {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    public OllamaService(ChatModel chatModel, EmbeddingModel embeddingModel) {
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    /** 텍스트를 벡터로 변환합니다. */
    public float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        return response.getResults().get(0).getOutput();
    }

    /** 프롬프트를 Qwen3 4B에 전달하고 답변 텍스트를 받습니다. */
    public String generate(String prompt) {
        return chatModel.call(prompt);
    }
}