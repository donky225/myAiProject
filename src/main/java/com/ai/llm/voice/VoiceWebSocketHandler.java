package com.ai.llm.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1단계: 실시간 음성 파이프라인의 배관(WebSocket 연결)만 검증하는 핸들러입니다.
 * 아직 STT/LLM/TTS 로직은 없고, 브라우저가 보내는 오디오 청크를 잘 받고 있는지
 * 콘솔 로그와 응답 메시지로 확인하는 용도입니다.
 */
@Component
public class VoiceWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceWebSocketHandler.class);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        log.info("[WS] 연결됨: {}", session.getId());
        session.getAttributes().put("chunkCount", new AtomicInteger(0));
        session.sendMessage(new TextMessage("{\"type\":\"connected\",\"message\":\"음성 WebSocket 연결됨\"}"));
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        int size = message.getPayloadLength();
        AtomicInteger counter = (AtomicInteger) session.getAttributes().get("chunkCount");
        int count = counter.incrementAndGet();

        log.info("[WS] 오디오 청크 수신: {}번째, {} bytes (session={})", count, size, session.getId());

        // 1단계 확인용: 받은 청크 수와 크기를 그대로 브라우저에 알려줍니다.
        session.sendMessage(new TextMessage(
                String.format("{\"type\":\"ack\",\"chunkIndex\":%d,\"bytes\":%d}", count, size)
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[WS] 연결 종료: {} (status={})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[WS] 전송 에러: {}", session.getId(), exception);
    }
}
