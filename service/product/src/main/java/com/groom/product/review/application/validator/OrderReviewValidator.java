package com.groom.product.review.application.validator;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.groom.common.presentation.advice.CustomException;
import com.groom.common.presentation.advice.ErrorCode;
import com.groom.product.review.domain.repository.ReviewRepository;
import com.groom.product.review.infrastructure.feign.OrderClient;
import com.groom.product.review.infrastructure.feign.dto.OrderReviewValidationRequest;
import com.groom.product.review.infrastructure.feign.dto.OrderReviewValidationResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderReviewValidator {

	private final OrderClient orderClient;
	private final ReviewRepository reviewRepository;

	public void validate(UUID orderId, UUID productId, UUID userId) {

		// 1️⃣ 리뷰 중복 체크 (Review 도메인 책임)
		reviewRepository.findByOrderIdAndProductId(orderId, productId)
			.ifPresent(r -> {
				throw new CustomException(ErrorCode.REVIEW_ALREADY_EXISTS);
			});

		//
		// 2️⃣ Order 서비스에 리뷰 검증 요청
		OrderReviewValidationResponse response =
			orderClient.validateReviewOrder(
				new OrderReviewValidationRequest(orderId, productId, userId)
			);

		// 3️⃣ 주문 존재 여부
		if (!response.isOrderExists()) {
			throw new CustomException(ErrorCode.ORDER_NOT_FOUND);
		}

		// 4️⃣ 주문 소유자 검증
		if (!response.isOwnerMatched()) {
			throw new CustomException(ErrorCode.FORBIDDEN);
		}

		// 5️⃣ 주문 상품 포함 여부
		if (!response.isContainsProduct()) {
			throw new CustomException(ErrorCode.INVALID_REQUEST);
		}

		// 6️⃣ 리뷰 가능한 주문 상태인지
		if (!response.isReviewable()) {
			throw new CustomException(ErrorCode.REVIEW_NOT_ALLOWED_ORDER_STATUS);
		}
	}
}
