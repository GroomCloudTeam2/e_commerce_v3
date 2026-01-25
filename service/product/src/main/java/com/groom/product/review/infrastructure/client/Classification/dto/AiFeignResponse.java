package com.groom.product.review.infrastructure.client.Classification.dto;


public record AiFeignResponse(
	String category,
	double confidence
) {}
