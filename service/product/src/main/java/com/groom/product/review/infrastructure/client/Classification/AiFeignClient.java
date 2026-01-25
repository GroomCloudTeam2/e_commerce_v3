package com.groom.product.review.infrastructure.client.Classification;

import com.groom.product.review.infrastructure.client.Classification.dto.AiFeignRequest;
import com.groom.product.review.infrastructure.client.Classification.dto.AiFeignResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
	name = "ai-classification",
	url = "${external.ai.classification.url}",
	fallback = AiFeignFallback.class
)
public interface AiFeignClient {

	@PostMapping("/infer")
    AiFeignResponse classify(AiFeignRequest request);
}
