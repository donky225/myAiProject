package com.ai.llm.voice;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final VoiceWebSocketHandler voiceWebSocketHandler;

    public WebSocketConfig(VoiceWebSocketHandler voiceWebSocketHandler) {
        this.voiceWebSocketHandler = voiceWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(voiceWebSocketHandler, "/ws/voice")
                .setAllowedOrigins("*"); // 개발 단계이므로 전체 허용, 배포 시 제한 권장
    }
}
