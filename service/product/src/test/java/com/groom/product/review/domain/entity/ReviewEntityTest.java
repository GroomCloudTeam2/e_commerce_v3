package com.groom.product.review.domain.entity;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewEntityTest {

    @Test
    @DisplayName("ReviewEntity 생성자(builder)로 필드가 정상 초기화된다")
    void builder_creates_entity_correctly() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ReviewEntity review = ReviewEntity.builder()
            .orderId(orderId)
            .productId(productId)
            .userId(userId)
            .rating(5)
            .content("아주 이뻐요")
            .category(ReviewCategory.DESIGN)
            .build();

        assertThat(review.getOrderId()).isEqualTo(orderId);
        assertThat(review.getProductId()).isEqualTo(productId);
        assertThat(review.getUserId()).isEqualTo(userId);
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("아주 이뻐요");
        assertThat(review.getCategory()).isEqualTo(ReviewCategory.DESIGN);
        assertThat(review.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("updateRating 호출 시 평점이 변경된다")
    void updateRating_changes_rating() {
        ReviewEntity review = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .rating(3)
            .content("보통")
            .category(ReviewCategory.PERFORMANCE)
            .build();

        review.updateRating(5);

        assertThat(review.getRating()).isEqualTo(5);
    }

    @Test
    @DisplayName("updateContentAndCategory 호출 시 내용과 카테고리가 함께 변경된다")
    void updateContentAndCategory_changes_fields() {
        ReviewEntity review = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .rating(4)
            .content("괜찮아요")
            .category(ReviewCategory.PERFORMANCE)
            .build();

        review.updateContentAndCategory("별로에요", ReviewCategory.PRICE);

        assertThat(review.getContent()).isEqualTo("별로에요");
        assertThat(review.getCategory()).isEqualTo(ReviewCategory.PRICE);
    }

    @Test
    @DisplayName("incrementLikeCount 호출 시 likeCount가 1 증가한다")
    void incrementLikeCount_increases_count() {
        ReviewEntity review = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .rating(5)
            .content("좋아요")
            .category(ReviewCategory.PRICE)
            .build();

        review.incrementLikeCount();
        review.incrementLikeCount();

        assertThat(review.getLikeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("decrementLikeCount는 likeCount가 0일 때 음수가 되지 않는다")
    void decrementLikeCount_does_not_go_below_zero() {
        ReviewEntity review = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .rating(5)
            .content("좋아요")
            .category(ReviewCategory.PRICE)
            .build();

        review.decrementLikeCount();

        assertThat(review.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("increment 후 decrement 호출 시 likeCount가 감소한다")
    void decrementLikeCount_decreases_count() {
        ReviewEntity review = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .rating(5)
            .content("좋아요")
            .category(ReviewCategory.PRICE)
            .build();

        review.incrementLikeCount();
        review.incrementLikeCount();
        review.decrementLikeCount();

        assertThat(review.getLikeCount()).isEqualTo(1);
    }
}
