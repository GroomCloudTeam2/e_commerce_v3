package com.groom.product.review.application.validator;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groom.e_commerce.global.presentation.advice.CustomException;
import com.groom.e_commerce.global.presentation.advice.ErrorCode;
import com.groom.e_commerce.review.domain.entity.ReviewEntity;
import com.groom.e_commerce.review.domain.repository.ReviewRepository;
import com.groom.e_commerce.review.infrastructure.feign.OrderClient;
import com.groom.e_commerce.review.infrastructure.feign.dto.OrderReviewValidationRequest;
import com.groom.e_commerce.review.infrastructure.feign.dto.OrderReviewValidationResponse;

@ExtendWith(MockitoExtension.class)
class OrderReviewValidatorTest {

    @Mock
    private OrderClient orderClient;

    @Mock
    private ReviewRepository reviewRepository;

    @InjectMocks
    private OrderReviewValidator validator;

    @Test
    @DisplayName("이미 리뷰가 존재하면 REVIEW_ALREADY_EXISTS 예외가 발생한다")
    void validate_fail_review_already_exists() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(reviewRepository.findByOrderIdAndProductId(orderId, productId))
            .thenReturn(Optional.of(mock(ReviewEntity.class)));

        assertThatThrownBy(() -> validator.validate(orderId, productId, userId))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_ALREADY_EXISTS);

        verify(orderClient, never()).validateReviewOrder(any());
    }

    @Test
    @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 예외가 발생한다")
    void validate_fail_order_not_found() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(reviewRepository.findByOrderIdAndProductId(orderId, productId))
            .thenReturn(Optional.empty());

        when(orderClient.validateReviewOrder(any(OrderReviewValidationRequest.class)))
            .thenReturn(new OrderReviewValidationResponse(
                false,  // orderExists
                true,
                true,
                null,
                true
            ));

        assertThatThrownBy(() -> validator.validate(orderId, productId, userId))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 소유자가 아니면 FORBIDDEN 예외가 발생한다")
    void validate_fail_owner_not_matched() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(reviewRepository.findByOrderIdAndProductId(orderId, productId))
            .thenReturn(Optional.empty());

        when(orderClient.validateReviewOrder(any()))
            .thenReturn(new OrderReviewValidationResponse(
                true,
                false, // ownerMatched
                true,
                "COMPLETED",
                true
            ));

        assertThatThrownBy(() -> validator.validate(orderId, productId, userId))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("주문에 상품이 포함되지 않으면 INVALID_REQUEST 예외가 발생한다")
    void validate_fail_product_not_in_order() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(reviewRepository.findByOrderIdAndProductId(orderId, productId))
            .thenReturn(Optional.empty());

        when(orderClient.validateReviewOrder(any()))
            .thenReturn(new OrderReviewValidationResponse(
                true,
                true,
                false, // containsProduct
                "COMPLETED",
                true
            ));

        assertThatThrownBy(() -> validator.validate(orderId, productId, userId))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("리뷰 가능한 주문 상태가 아니면 REVIEW_NOT_ALLOWED_ORDER_STATUS 예외가 발생한다")
    void validate_fail_not_reviewable() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(reviewRepository.findByOrderIdAndProductId(orderId, productId))
            .thenReturn(Optional.empty());

        when(orderClient.validateReviewOrder(any()))
            .thenReturn(new OrderReviewValidationResponse(
                true,
                true,
                true,
                "CANCELLED",
                false // reviewable
            ));

        assertThatThrownBy(() -> validator.validate(orderId, productId, userId))
            .isInstanceOf(CustomException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_NOT_ALLOWED_ORDER_STATUS);
    }

    @Test
    @DisplayName("모든 검증을 통과하면 예외가 발생하지 않는다")
    void validate_success() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(reviewRepository.findByOrderIdAndProductId(orderId, productId))
            .thenReturn(Optional.empty());

        when(orderClient.validateReviewOrder(any()))
            .thenReturn(new OrderReviewValidationResponse(
                true,
                true,
                true,
                "COMPLETED",
                true
            ));

        assertThatCode(() -> validator.validate(orderId, productId, userId))
            .doesNotThrowAnyException();
    }
}
