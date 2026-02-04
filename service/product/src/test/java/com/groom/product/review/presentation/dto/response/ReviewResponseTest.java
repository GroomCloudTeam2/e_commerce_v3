package com.groom.product.review.presentation.dto.response;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.groom.product.review.domain.entity.ReviewCategory;
import com.groom.product.review.domain.entity.ReviewEntity;
import com.groom.product.review.infrastructure.redis.ReviewReadModel;

class ReviewResponseTest {

    @Test
    @DisplayName("fromEntity는 ReviewEntity의 값을 그대로 매핑한다")
    void fromEntity_maps_fields_correctly() {
        UUID reviewId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ReviewEntity entity = ReviewEntity.builder()
                .orderId(orderId)
                .productId(productId)
                .userId(userId)
                .rating(5)
                .content("배터리가 오래가요")
                .category(ReviewCategory.PERFORMANCE)
                .build();

        ReviewResponse response = ReviewResponse.fromEntity(entity);

        assertThat(response.getOrderId()).isEqualTo(orderId);
        assertThat(response.getProductId()).isEqualTo(productId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getContent()).isEqualTo("배터리가 오래가요");
        assertThat(response.getCategory()).isEqualTo(ReviewCategory.PERFORMANCE);
    }

    @Test
    @DisplayName("fromReadModel은 ReadModel 값을 기반으로 응답을 생성한다")
    void fromReadModel_maps_fields_correctly() {
        UUID reviewId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        ReviewReadModel readModel = ReviewReadModel.builder()
                .reviewId(reviewId)
                .userId(userId)
                .rating(4)
                .content("괜찮아요")
                .createdAt(createdAt)
                .build();

        ReviewResponse response = ReviewResponse.fromReadModel(readModel);

        assertThat(response.getReviewId()).isEqualTo(reviewId);
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getContent()).isEqualTo("괜찮아요");
        assertThat(response.getCreatedAt()).isEqualTo(createdAt);
        assertThat(response.getRating()).isEqualTo(0);
    }
}
