package com.groom.product.review.infrastructure.client.Classification;

import com.groom.product.review.infrastructure.client.Classification.dto.AiFeignRequest;
import com.groom.product.review.infrastructure.client.Classification.dto.AiFeignResponse;
import org.springframework.stereotype.Component;

@Component
public class AiFeignFallback implements AiFeignClient {

	@Override
	public AiFeignResponse classify(AiFeignRequest request) {
		return new AiFeignResponse("ERR", 0.0);
	}
}
