package com.groom.product.review.infrastructure.client.OpenAi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChatCompletionResponse {
	private List<Choice> choices;

	public String getContent() {
		return choices.get(0).getMessage().getContent();
	}
}
