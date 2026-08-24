package com.ai.llm.voice;

import com.ai.llm.ollama.OllamaService;
import com.ai.llm.websearch.WebSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 3~4단계: STT 전사 -> (필요시 웹 검색으로 실시간 정보 보강) -> LLM 스트리밍 답변 생성
 * -> 문장 단위로 즉시 클라이언트에 전송.
 *
 * 검색 필요 여부는 짧은 판단용 LLM 호출로 먼저 결정합니다 (모델이 "SEARCH: 검색어"
 * 또는 "NONE"만 출력하도록 강하게 지시). 이후 실제 답변은 항상 스트리밍으로 생성합니다.
 */
@Component
public class VoiceWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(VoiceWebSocketHandler.class);
    private static final Pattern SEARCH_PATTERN = Pattern.compile("SEARCH:\\s*(.+)", Pattern.CASE_INSENSITIVE);

    private final Map<String, ByteArrayOutputStream> audioBuffers = new ConcurrentHashMap<>();

    private final VoiceService voiceService;
    private final OllamaService ollamaService;
    private final WebSearchService webSearchService;

    public VoiceWebSocketHandler(VoiceService voiceService,
                                 OllamaService ollamaService,
                                 WebSearchService webSearchService) {
        this.voiceService = voiceService;
        this.ollamaService = ollamaService;
        this.webSearchService = webSearchService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        log.info("[WS] 연결됨: {}", session.getId());
        audioBuffers.put(session.getId(), new ByteArrayOutputStream());
        sendJson(session, "connected", "음성 WebSocket 연결됨");
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        ByteArrayOutputStream buffer = audioBuffers.get(session.getId());
        if (buffer == null) return;

        byte[] chunk = new byte[message.getPayloadLength()];
        message.getPayload().get(chunk);
        buffer.write(chunk);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload.contains("end-of-utterance")) {
            handleUtteranceEnd(session);
        }
    }

    private void handleUtteranceEnd(WebSocketSession session) throws IOException {
        ByteArrayOutputStream buffer = audioBuffers.get(session.getId());
        if (buffer == null || buffer.size() == 0) {
            log.info("[WS] 발화 종료 신호 수신했으나 오디오 없음: session={}", session.getId());
            return;
        }

        byte[] audioBytes = buffer.toByteArray();
        buffer.reset();

        log.info("[WS] 발화 종료 -> STT 전사 시작: session={}, {} bytes", session.getId(), audioBytes.length);
        sendJson(session, "transcribing", null);

        String transcript;
        try {
            Map<String, Object> result = voiceService.speechToText(audioBytes, "utterance.webm");
            transcript = result.get("text") == null ? "" : result.get("text").toString();
        } catch (Exception e) {
            log.error("[WS] STT 처리 실패", e);
            sendJson(session, "error", "STT 처리 중 오류: " + e.getMessage());
            return;
        }

        if (transcript.isBlank()) {
            sendJson(session, "error", "인식된 음성이 없습니다.");
            return;
        }

        log.info("[WS] 전사 결과: \"{}\"", transcript);
        sendTranscript(session, transcript);

        // 1) 검색이 필요한 질문인지 먼저 짧게 판단
        String searchQuery = decideSearchQuery(transcript);

        String finalPrompt;
        if (searchQuery != null) {
            log.info("[WS] 웹 검색 필요 판단됨: \"{}\"", searchQuery);
            sendJson(session, "searching", searchQuery);

            String searchResults;
            try {
                searchResults = webSearchService.search(searchQuery);
            } catch (Exception e) {
                log.error("[WS] 웹 검색 실패", e);
                searchResults = "";
            }

            if (searchResults.isBlank()) {
                finalPrompt = buildKoreanOnlyPrompt(transcript);
            } else {
                finalPrompt = """
                        당신은 한국어로만 답변하는 어시스턴트입니다. 아래는 방금 검색한 최신 웹 검색 결과입니다.
                        이 정보를 참고해서 사용자 질문에 정확하고 자연스러운 한국어로 답변하세요.
                        검색 결과에 없는 내용은 추측하지 마세요.

                        [검색 결과]
                        %s

                        [질문]
                        %s
                        """.formatted(searchResults, transcript);
            }
        } else {
            finalPrompt = buildKoreanOnlyPrompt(transcript);
        }

        streamAnswer(session, finalPrompt);
    }

    private String buildKoreanOnlyPrompt(String question) {
        return """
                당신은 한국어로만 답변하는 어시스턴트입니다. 질문이 어떤 언어로 오든,
                답변은 반드시 자연스러운 한국어로만 작성하세요. 영어나 다른 언어를 섞지 마세요.

                질문: %s
                """.formatted(question);
    }

    /**
     * 짧은 판단용 호출로 웹 검색이 필요한지 확인합니다.
     * 필요하면 검색어 문자열을, 필요 없으면 null을 반환합니다.
     */
    private String decideSearchQuery(String question) {
        String routingPrompt = """
                아래 질문에 정확히 답하려면 실시간/최신 정보(예: 오늘 날짜 기준 날씨, 최근 뉴스,
                현재 주가, 최신 사실 등)를 웹에서 검색해야 하나요?

                검색이 필요하면 정확히 이 형식으로만 답하세요 (다른 말 절대 추가 금지):
                SEARCH: 검색에 사용할 핵심 키워드

                검색이 필요 없으면 (일반 지식, 인사, 대화 등) 정확히 이 단어만 답하세요:
                NONE

                질문: %s
                """.formatted(question);

        try {
            String decision = ollamaService.generate(routingPrompt).trim();
            Matcher matcher = SEARCH_PATTERN.matcher(decision);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
            return null;
        } catch (Exception e) {
            log.error("[WS] 검색 필요 여부 판단 실패, 검색 없이 진행", e);
            return null;
        }
    }

    private void streamAnswer(WebSocketSession session, String prompt) throws IOException {
        sendJson(session, "answering", null);

        StringBuilder sentenceBuffer = new StringBuilder();

        try {
            ollamaService.generateStream(prompt, token -> {
                sentenceBuffer.append(token);

                int endIdx = findSentenceEnd(sentenceBuffer);
                while (endIdx != -1) {
                    String sentence = sentenceBuffer.substring(0, endIdx + 1).trim();
                    if (!sentence.isBlank()) {
                        sendSentenceSafely(session, sentence);
                    }
                    sentenceBuffer.delete(0, endIdx + 1);
                    endIdx = findSentenceEnd(sentenceBuffer);
                }
            });
        } catch (Exception e) {
            log.error("[WS] LLM 스트리밍 실패", e);
            sendJson(session, "error", "LLM 응답 중 오류: " + e.getMessage());
            return;
        }

        String remaining = sentenceBuffer.toString().trim();
        if (!remaining.isBlank()) {
            sendSentenceSafely(session, remaining);
        }

        sendJson(session, "answer_complete", null);
        log.info("[WS] 답변 스트리밍 완료: session={}", session.getId());
    }

    private int findSentenceEnd(StringBuilder buffer) {
        for (int i = 0; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                return i;
            }
        }
        return -1;
    }

    private void sendSentenceSafely(WebSocketSession session, String sentence) {
        try {
            log.info("[WS] 문장 완성: \"{}\"", sentence);
            String json = String.format("{\"type\":\"sentence\",\"text\":\"%s\"}", escape(sentence));
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("[WS] 문장 전송 실패", e);
        }
    }

    private void sendJson(WebSocketSession session, String type, String message) throws IOException {
        if (!session.isOpen()) return;
        String json = message == null
                ? String.format("{\"type\":\"%s\"}", escape(type))
                : String.format("{\"type\":\"%s\",\"message\":\"%s\"}", escape(type), escape(message));
        session.sendMessage(new TextMessage(json));
    }

    private void sendTranscript(WebSocketSession session, String text) throws IOException {
        if (!session.isOpen()) return;
        String json = String.format("{\"type\":\"transcript\",\"text\":\"%s\"}", escape(text));
        session.sendMessage(new TextMessage(json));
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[WS] 연결 종료: {} (status={})", session.getId(), status);
        audioBuffers.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[WS] 전송 에러: {}", session.getId(), exception);
    }
}
