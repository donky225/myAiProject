package com.ai.llm.rag;

import java.util.List;

// 평가 API에서 사용할 결과 DTO. 답변과 함께 검색된 컨텍스트, 소요시간을 담습니다.
public record RagAnswer(
        String question,
        String answer,
        List<String> contexts,
        long elapsedMillis
) {}

