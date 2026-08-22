package com.ai.llm.pgvector;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PgVectorIngestService {

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

    public int ingestPdf(MultipartFile file) throws IOException {
        String text = pdfTextExtractionService.extractText(file);
        String title = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
        List<Document> chunks = chunk(text, title);
        pgVectorService.add(chunks);
        return chunks.size();
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
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
        for (String rawChunk : rawChunks) {
            if (isNoiseChunk(rawChunk)) continue;

            String content = "[" + sourceName + "] " + rawChunk;
            Document doc = new Document(
                    content,
                    Map.of("source", sourceName, "chunkIndex", index)
            );
            documents.add(doc);
            index++;
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