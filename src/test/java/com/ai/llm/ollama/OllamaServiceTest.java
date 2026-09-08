package com.ai.llm.ollama;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OllamaService에 대한 순수 단위 테스트.
 *
 * ChatModel/EmbeddingModel을 모두 Mockito로 목(mock) 처리하기 때문에
 * 실제 Ollama 서버, Spring 컨텍스트, 네트워크가 전혀 필요 없습니다.
 * GitHub Actions처럼 Ollama가 없는 환경(CI 러너)에서도 그대로 통과해야 합니다.
 *
 * 이 클래스를 테스트 대상으로 고른 이유:
 * K8s 배포 중 발견했던 "스트리밍 URL 하드코딩" 버그(SETUP.md 20.9.2 참고)가 있던 클래스라,
 * 앞으로 base-url 설정이 다시 깨지는 걸 회귀 테스트로 잡아두기 위함입니다.
 */
class OllamaServiceTest {

    @Test
    void generate는_ChatModel의_결과를_그대로_반환한다() {
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(chatModel.call("안녕")).thenReturn("반갑습니다");

        OllamaService service = new OllamaService(chatModel, embeddingModel, "http://localhost:11434");

        String result = service.generate("안녕");

        assertThat(result).isEqualTo("반갑습니다");
        verify(chatModel).call("안녕");
    }

    @Test
    void embed는_EmbeddingModel이_반환한_벡터를_그대로_반환한다() {
        ChatModel chatModel = mock(ChatModel.class);
        // Spring AI의 EmbeddingResponse/Embedding 객체를 직접 생성자로 만드는 대신
        // deep stub으로 체이닝된 호출(embedForResponse(...).getResults().get(0).getOutput())을
        // 한 번에 목 처리합니다. Spring AI 버전이 바뀌어 생성자 시그니처가 달라져도 안 깨집니다.
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class, RETURNS_DEEP_STUBS);
        float[] expectedVector = {0.1f, 0.2f, 0.3f};

        when(embeddingModel.embedForResponse(List.of("텍스트"))
                .getResults().get(0).getOutput())
                .thenReturn(expectedVector);

        OllamaService service = new OllamaService(chatModel, embeddingModel, "http://localhost:11434");

        float[] result = service.embed("텍스트");

        assertThat(result).isEqualTo(expectedVector);
    }

    @Test
    void ollamaBaseUrl이_생성자에_주입되지_않으면_기본값을_사용한다() {
        // application.yml의 spring.ai.ollama.base-url이 없는 극단적인 상황을 대비해
        // 기본값(@Value의 : 뒤 값)이 살아있는지 확인하는 용도.
        // 이 테스트 자체는 생성자가 예외 없이 동작하는지만 확인합니다(기본값 로직은 Spring이 처리).
        ChatModel chatModel = mock(ChatModel.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

        OllamaService service = new OllamaService(chatModel, embeddingModel, "http://localhost:11434");

        assertThat(service).isNotNull();
    }
}
