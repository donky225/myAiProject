package com.ai.llm.pgvector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PgVectorIngestService {

    private static final Logger log = LoggerFactory.getLogger(PgVectorIngestService.class);

    private static final int MAX_CHUNK_SIZE = 1024;
    private static final int OVERLAP_SENTENCES = 1;

    private static final Pattern SENTENCE_END_PATTERN =
            Pattern.compile("[.!?](?=\\s|$)");

    /**
     * 목차(TOC)의 점선/가운뎃점 줄을 걸러내기 위한 패턴입니다.
     * 예: "1. 임원 및 직원 등의 현황..................217"
     * 마침표/가운뎃점이 10개 이상 연속되면 실제 내용이 아닌 목차 노이즈로 판단합니다.
     */
    private static final Pattern TOC_NOISE_PATTERN =
            Pattern.compile("[.·]{10,}");

    /**
     * 대괄호 파일명 접두사를 뗀 실질 내용 길이가 이보다 짧으면 제외합니다.
     */
    private static final int MIN_MEANINGFUL_LENGTH = 30;

    private final PgVectorService pgVectorService;

    private final PdfTextExtractionService pdfTextExtractionService;

    public PgVectorIngestService(PgVectorService pgVectorService,
                                 PdfTextExtractionService pdfTextExtractionService) {
        this.pgVectorService = pgVectorService;
        this.pdfTextExtractionService = pdfTextExtractionService;
    }

    /**
     * 메서드명은 하위 호환을 위해 유지하지만, 실제로는 PDF뿐 아니라
     * 일반 텍스트(.txt) 파일도 처리합니다 (버그 수정: 예전엔 파일 형식과
     * 무관하게 무조건 PDF로 파싱을 시도해, .txt 업로드가 항상
     * "End-of-File, expected line" 에러로 실패했습니다).
     */
    public int ingestPdf(MultipartFile file) throws IOException {
        String title = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        String text = extractTextByType(file, title);
        List<Document> chunks = chunk(text, title);
        pgVectorService.add(chunks);
        return chunks.size();
    }

    /**
     * 파일 확장자와 Content-Type을 확인해 PDF는 PdfTextExtractionService로,
     * 그 외(텍스트 파일 등)는 UTF-8로 직접 읽어 반환합니다.
     */
    private String extractTextByType(MultipartFile file, String filename) throws IOException {
        String lowerName = filename.toLowerCase();
        String contentType = file.getContentType();
        boolean looksLikePdf = lowerName.endsWith(".pdf")
                || "application/pdf".equals(contentType);

        if (looksLikePdf) {
            log.debug("PDF로 판단, PdfTextExtractionService로 파싱: {}", filename);
            return pdfTextExtractionService.extractText(file);
        }

        log.debug("텍스트 파일로 판단, UTF-8로 직접 읽음: {} (contentType={})", filename, contentType);
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    /**
     * 목차 점선/가운뎃점으로만 이루어진, 실질 정보가 없는 청크인지 판단합니다.
     */
    private boolean isNoiseChunk(String rawChunk) {
        if (rawChunk == null) return true;
        String withoutSpaces = rawChunk.replaceAll("\\s", "");
        if (withoutSpaces.length() < MIN_MEANINGFUL_LENGTH) return true;
        return TOC_NOISE_PATTERN.matcher(rawChunk).find();
    }

    private List<Document> chunk(String text, String sourceName) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        String normalized = text.replaceAll("[ \\t\\r\\n]+", " ").trim();
        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> sentences = splitIntoSentences(normalized);
        List<String> rawChunks = groupSentencesIntoChunks(sentences);

        // 목차 점선처럼 실질 정보가 없는 청크는 임베딩/저장 대상에서 제외합니다.
        List<Document> documents = new ArrayList<>();
        int index = 0;
        int skippedAsNoise = 0;
        for (String rawChunk : rawChunks) {
            if (isNoiseChunk(rawChunk)) {
                skippedAsNoise++;
                continue;
            }

            String content = "[" + sourceName + "] " + rawChunk;
            Document doc = new Document(
                    content,
                    Map.of("source", sourceName, "chunkIndex", index)
            );
            documents.add(doc);
            index++;
        }

        if (skippedAsNoise > 0) {
            log.info("청킹 완료: {}건 저장, {}건 노이즈(30자 미만)로 제외 [{}]",
                    documents.size(), skippedAsNoise, sourceName);
        }

        return documents;
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        Matcher matcher = SENTENCE_END_PATTERN.matcher(text);
        int lastIndex = 0;

        while (matcher.find()) {
            int sentenceEnd = matcher.end();
            String sentence = text.substring(lastIndex, sentenceEnd).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
            lastIndex = sentenceEnd;
        }

        if (lastIndex < text.length()) {
            String sentence = text.substring(lastIndex).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }

        return sentences;
    }

    private List<String> groupSentencesIntoChunks(List<String> sentences) {
        List<String> chunks = new ArrayList<>();
        if (sentences == null || sentences.isEmpty()) {
            return chunks;
        }

        List<String> currentSentences = new ArrayList<>();

        for (String sentence : sentences) {
            if (sentence == null || sentence.isBlank()) continue;
            sentence = sentence.trim();

            if (currentSentences.isEmpty()) {
                currentSentences.add(sentence);
                if (sentence.length() > MAX_CHUNK_SIZE) {
                    chunks.add(sentence);
                }
                continue;
            }

            int currentLength = getSentencesLength(currentSentences);
            int newLength = currentLength + 1 + sentence.length();

            if (newLength > MAX_CHUNK_SIZE) {
                String chunk = joinSentences(currentSentences);
                if (!chunk.isBlank()) {
                    chunks.add(chunk);
                }

                List<String> nextSentences = new ArrayList<>();
                int overlapStart = Math.max(0, currentSentences.size() - OVERLAP_SENTENCES);
                for (int i = overlapStart; i < currentSentences.size(); i++) {
                    nextSentences.add(currentSentences.get(i));
                }
                nextSentences.add(sentence);
                currentSentences = nextSentences;

                int nextLength = getSentencesLength(currentSentences);
                if (nextLength > MAX_CHUNK_SIZE) {
                    if (sentence.length() <= MAX_CHUNK_SIZE) {
                        currentSentences.clear();
                        currentSentences.add(sentence);
                    } else {
                        chunks.add(sentence);
                        currentSentences.clear();
                        currentSentences.add(sentence);
                    }
                }
            } else {
                currentSentences.add(sentence);
            }
        }

        if (!currentSentences.isEmpty()) {
            String lastChunk = joinSentences(currentSentences);
            if (!lastChunk.isBlank()) {
                chunks.add(lastChunk);
            }
        }

        return chunks;
    }

    private int getSentencesLength(List<String> sentences) {
        if (sentences == null || sentences.isEmpty()) return 0;
        int length = 0;
        for (String sentence : sentences) {
            if (sentence == null || sentence.isBlank()) continue;
            if (length > 0) length += 1;
            length += sentence.length();
        }
        return length;
    }

    private String joinSentences(List<String> sentences) {
        return String.join(" ", sentences).trim();
    }
}