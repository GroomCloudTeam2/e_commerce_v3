package com.groom.product.review.infrastructure.client.OpenAi;

import com.groom.product.review.infrastructure.client.OpenAi.dto.ChatCompletionRequest;
import com.groom.product.review.infrastructure.client.OpenAi.dto.ChatCompletionResponse;
import com.groom.product.review.infrastructure.client.OpenAi.dto.Choice;
import com.groom.product.review.infrastructure.client.OpenAi.dto.Message;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenAiFeignFallback implements OpenAiFeignClient {

	@Override
	public ChatCompletionResponse createChatCompletion(
		ChatCompletionRequest request
	) {
		// 정책: 요약 실패 시 null or 빈 응답
		return new ChatCompletionResponse(
			List.of(
				new Choice(
					new Message("assistant", "")
				)
			)
		);
	}
}
