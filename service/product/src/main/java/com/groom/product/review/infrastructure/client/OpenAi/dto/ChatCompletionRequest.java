package com.groom.product.review.infrastructure.client.OpenAi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatCompletionRequest {
	private String model;
	private List<Message> messages;
	private double temperature;
}
