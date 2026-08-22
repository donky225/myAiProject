package com.ai.llm.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TextChunker {

    /**
     * 최대 Chunk 크기
     *
     * 문장을 기준으로 Chunk를 구성하기 때문에
     * 실제 Chunk 크기는 1024자보다 조금 작거나 같게 구성됩니다.
     *
     * 단, 문장 하나 자체가 1024자를 초과하는 경우에는
     * 해당 문장을 하나의 Chunk로 그대로 사용합니다.
     */
    private static final int MAX_CHUNK_SIZE = 1024;

    /**
     * 이전 Chunk와 다음 Chunk 사이에
     * 마지막 문장 1개를 겹쳐서 사용합니다.
     *
     * 예:
     *
     * Chunk 1
     * A. B. C.
     *
     * Chunk 2
     * C. D. E.
     */
    private static final int OVERLAP_SENTENCES = 1;


    /**
     * 문장 분리 정규식
     *
     * 지원:
     *   .
     *   !
     *   ?
     *
     * 중요한 점:
     *
     * 20.79%
     *
     * 에서 "." 뒤에는 숫자 "7"이 나오므로
     * 문장 끝으로 판단하지 않습니다.
     *
     * 즉,
     *
     * 20.79%이다.
     *
     * 의 첫 번째 "."은 무시하고
     * "이다."의 "."에서 문장이 종료됩니다.
     *
     * 또한
     *
     * 2.0. 다음
     *
     * 같은 경우에는 마지막 "." 뒤가 공백이므로
     * 문장 종료로 판단합니다.
     */
    private static final Pattern SENTENCE_END_PATTERN =
            Pattern.compile("[.!?](?=\\s|$)");


    /**
     * Text를 문장 단위로 Chunking
     */
    public List<String> chunk(String text) {

        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        /*
         * 연속된 공백을 하나로 정리
         *
         * 줄바꿈도 하나의 공백으로 처리합니다.
         */
        String normalized = text
                .replaceAll("[ \\t\\r\\n]+", " ")
                .trim();

        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }

        /*
         * 1. 문장 단위로 분리
         */
        List<String> sentences = splitIntoSentences(normalized);

        /*
         * 2. 문장을 1024자 기준으로 묶음
         * 3. 이전 Chunk의 마지막 문장 1개를 다음 Chunk에 overlap
         */
        return groupSentencesIntoChunks(sentences);
    }


    /**
     * 문장 단위 분리
     */
    private List<String> splitIntoSentences(String text) {

        List<String> sentences = new ArrayList<>();

        Matcher matcher = SENTENCE_END_PATTERN.matcher(text);

        int lastIndex = 0;

        while (matcher.find()) {

            /*
             * 문장 끝 위치
             *
             * 예:
             *
             * "정부가 발표했다. 다음"
             *
             * matcher.end()
             * -> "." 다음 위치
             */
            int sentenceEnd = matcher.end();

            String sentence = text
                    .substring(lastIndex, sentenceEnd)
                    .trim();

            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }

            /*
             * 다음 문장 시작 위치
             */
            lastIndex = sentenceEnd;
        }

        /*
         * 마지막 문장 처리
         *
         * 문장 끝에 ., !, ?가 없는 경우에도
         * 마지막 내용을 하나의 문장으로 처리합니다.
         */
        if (lastIndex < text.length()) {

            String sentence = text
                    .substring(lastIndex)
                    .trim();

            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }

        return sentences;
    }


    /**
     * 문장들을 1024자 기준으로 Chunk 구성
     *
     * 핵심:
     *
     * Chunk 1:
     * A. B. C.
     *
     * Chunk 2:
     * C. D. E.
     *
     * Chunk 3:
     * E. F. G.
     *
     * 이런 식으로 이전 Chunk의 마지막 문장 1개를
     * 다음 Chunk의 시작에 포함합니다.
     */
    private List<String> groupSentencesIntoChunks(List<String> sentences) {

        List<String> chunks = new ArrayList<>();

        if (sentences == null || sentences.isEmpty()) {
            return chunks;
        }

        /*
         * 현재 Chunk에 들어갈 문장들
         */
        List<String> currentSentences = new ArrayList<>();


        for (String sentence : sentences) {

            if (sentence == null || sentence.isBlank()) {
                continue;
            }

            sentence = sentence.trim();


            /*
             * 현재 Chunk가 비어있는 경우
             */
            if (currentSentences.isEmpty()) {

                currentSentences.add(sentence);

                /*
                 * 문장 하나 자체가 1024자를 초과해도
                 * 해당 문장은 하나의 Chunk로 처리합니다.
                 */
                if (sentence.length() > MAX_CHUNK_SIZE) {

                    chunks.add(sentence);

                    /*
                     * 다음 Chunk에는 이 문장을 overlap으로 넣을지
                     * 결정하기 위해 현재 문장을 유지합니다.
                     *
                     * 하지만 1024자를 초과하는 문장을 계속
                     * overlap하면 다음 Chunk도 1024자를 초과할 수 있으므로
                     * 여기서는 다음 일반 문장으로 넘어갈 수 있도록
                     * 마지막 문장만 별도로 기억합니다.
                     */
                    currentSentences.clear();
                    currentSentences.add(sentence);
                }

                continue;
            }


            /*
             * 현재 Chunk의 길이 계산
             */
            int currentLength = getSentencesLength(currentSentences);

            /*
             * 현재 문장을 추가했을 때의 길이
             */
            int newLength;

            if (currentSentences.isEmpty()) {
                newLength = sentence.length();
            } else {
                newLength = currentLength
                        + 1
                        + sentence.length();
            }


            /*
             * 1024자를 초과하는 경우
             */
            if (newLength > MAX_CHUNK_SIZE) {

                /*
                 * 현재 Chunk 저장
                 */
                String chunk = joinSentences(currentSentences);

                if (!chunk.isBlank()) {
                    chunks.add(chunk);
                }


                /*
                 * 중요:
                 *
                 * 이전 Chunk의 마지막 문장 1개를
                 * 다음 Chunk에 overlap
                 */
                List<String> nextSentences = new ArrayList<>();

                int overlapStart = Math.max(
                        0,
                        currentSentences.size() - OVERLAP_SENTENCES
                );

                for (int i = overlapStart;
                     i < currentSentences.size();
                     i++) {

                    nextSentences.add(currentSentences.get(i));
                }


                /*
                 * 현재 문장 추가
                 *
                 * 예:
                 *
                 * 이전:
                 * C.
                 *
                 * 현재:
                 * D.
                 *
                 * 다음:
                 * C. D.
                 */
                nextSentences.add(sentence);


                currentSentences = nextSentences;


                /*
                 * 만약 overlap된 마지막 문장 + 현재 문장만으로
                 * 1024자를 초과하는 경우
                 *
                 * 현재 문장을 별도 Chunk로 처리합니다.
                 *
                 * 단, 문장 자체가 1024자를 초과하는 경우는
                 * 어쩔 수 없이 단독 Chunk로 허용합니다.
                 */
                int nextLength = getSentencesLength(currentSentences);

                if (nextLength > MAX_CHUNK_SIZE) {

                    /*
                     * 현재 문장 자체가 1024자보다 작은 경우
                     * overlap 때문에 초과한 것이므로
                     * overlap을 제거하고 현재 문장만 사용합니다.
                     */
                    if (sentence.length() <= MAX_CHUNK_SIZE) {

                        currentSentences.clear();
                        currentSentences.add(sentence);

                    } else {

                        /*
                         * 문장 자체가 1024자보다 큰 경우
                         * 단독 Chunk
                         */
                        chunks.add(sentence);

                        currentSentences.clear();

                        /*
                         * 다음 Chunk overlap을 위해
                         * 현재 문장을 기억
                         */
                        currentSentences.add(sentence);
                    }
                }

            } else {

                /*
                 * 1024자 이내이면 현재 Chunk에 추가
                 */
                currentSentences.add(sentence);
            }
        }


        /*
         * 마지막 Chunk 처리
         */
        if (!currentSentences.isEmpty()) {

            String lastChunk = joinSentences(currentSentences);

            if (!lastChunk.isBlank()) {
                chunks.add(lastChunk);
            }
        }

        return chunks;
    }


    /**
     * 문장 리스트의 전체 길이 계산
     *
     * 문장 사이에는 공백 하나가 들어간다고 계산합니다.
     */
    private int getSentencesLength(List<String> sentences) {

        if (sentences == null || sentences.isEmpty()) {
            return 0;
        }

        int length = 0;

        for (String sentence : sentences) {

            if (sentence == null || sentence.isBlank()) {
                continue;
            }

            if (length > 0) {
                length += 1;
            }

            length += sentence.length();
        }

        return length;
    }


    /**
     * 문장 리스트를 하나의 Chunk 문자열로 결합
     */
    private String joinSentences(List<String> sentences) {

        return String.join(" ", sentences).trim();
    }
}