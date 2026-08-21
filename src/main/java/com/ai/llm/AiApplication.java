package com.ai.llm;

import org.springframework.ai.vectorstore.opensearch.autoconfigure.OpenSearchVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
		OpenSearchVectorStoreAutoConfiguration.class
})
public class AiApplication {
	public static void main(String[] args) {
		SpringApplication.run(AiApplication.class, args);
	}
}