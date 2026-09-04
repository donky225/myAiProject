package com.ai.llm.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot가 자동 생성하는 KafkaTemplate은 <Object, Object> 타입이라
 * DocumentIngestionEvent 전용 KafkaTemplate<String, DocumentIngestionEvent>가
 * 필요한 곳에서 빈을 못 찾는 문제가 생길 수 있습니다. 여기서 명시적으로 등록합니다.
 *
 * @EnableKafka: 이게 없으면 @KafkaListener 어노테이션이 있어도 실제 리스너
 * 컨테이너(Consumer)가 뜨지 않습니다 (Producer는 정상 동작해서 헷갈리기 쉬운 문제).
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, DocumentIngestionEvent> documentIngestionProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, DocumentIngestionEvent> documentIngestionKafkaTemplate() {
        return new KafkaTemplate<>(documentIngestionProducerFactory());
    }
}