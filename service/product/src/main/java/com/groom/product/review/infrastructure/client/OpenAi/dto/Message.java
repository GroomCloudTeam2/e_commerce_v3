package com.groom.product.review.infrastructure.client.OpenAi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Message {
	private String role;
	private String content;
}
