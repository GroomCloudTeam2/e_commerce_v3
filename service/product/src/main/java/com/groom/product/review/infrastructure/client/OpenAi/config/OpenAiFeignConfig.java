package com.groom.product.review.infrastructure.client.OpenAi.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class OpenAiFeignConfig {

	@Value("${ai.openai.api-key}")
	private String apiKey;

	@Bean
	public RequestInterceptor openAiAuthInterceptor() {
		return requestTemplate -> {
			requestTemplate.header(
				HttpHeaders.AUTHORIZATION,
				"Bearer " + apiKey
			);
			requestTemplate.header(
				HttpHeaders.CONTENT_TYPE,
				"application/json"
			);
		};
	}
}
