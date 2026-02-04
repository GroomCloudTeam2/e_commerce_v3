package com.groom.product.review.domain.entity;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductRatingEntityTest {

    @Test
    @DisplayName("생성자 호출 시 productId와 초기값이 설정된다")
    void constructor_initializes_fields() {
        UUID productId = UUID.randomUUID();

        ProductRatingEntity rating = new ProductRatingEntity(productId);

        assertThat(rating.getProductId()).isEqualTo(productId);
        assertThat(rating.getAvgRating()).isEqualTo(0.0);
        assertThat(rating.getReviewCount()).isZero();
        assertThat(rating.getAiReview()).isNull();
    }

    @Test
    @DisplayName("updateRating 호출 시 평균 평점과 리뷰 수가 증가한다")
    void updateRating_increases_avg_and_count() {
        ProductRatingEntity rating = new ProductRatingEntity(UUID.randomUUID());

        rating.updateRating(4);
        rating.updateRating(5);

        assertThat(rating.getReviewCount()).isEqualTo(2);
        assertThat(rating.getAvgRating()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("updateRating는 소수점 첫째 자리에서 반올림한다")
    void updateRating_rounds_to_one_decimal() {
        ProductRatingEntity rating = new ProductRatingEntity(UUID.randomUUID());

        rating.updateRating(4); // 4.0
        rating.updateRating(5); // 4.5
        rating.updateRating(4); // 4.333...

        assertThat(rating.getAvgRating()).isEqualTo(4.3);
    }

    @Test
    @DisplayName("removeRating 호출 시 리뷰 수와 평균 평점이 감소한다")
    void removeRating_decreases_avg_and_count() {
        ProductRatingEntity rating = new ProductRatingEntity(UUID.randomUUID());

        rating.updateRating(5);
        rating.updateRating(3); // avg 4.0

        rating.removeRating(3);

        assertThat(rating.getReviewCount()).isEqualTo(1);
        assertThat(rating.getAvgRating()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("removeRating는 리뷰가 1개 이하일 때 초기 상태로 리셋된다")
    void removeRating_resets_when_last_review_removed() {
        ProductRatingEntity rating = new ProductRatingEntity(UUID.randomUUID());

        rating.updateRating(4);

        rating.removeRating(4);

        assertThat(rating.getReviewCount()).isZero();
        assertThat(rating.getAvgRating()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("updateAiReview 호출 시 AI 요약이 저장된다")
    void updateAiReview_sets_value() {
        ProductRatingEntity rating = new ProductRatingEntity(UUID.randomUUID());

        rating.updateAiReview("AI SUMMARY TEXT");

        assertThat(rating.getAiReview()).isEqualTo("AI SUMMARY TEXT");
    }

    @Test
    @DisplayName("reset 호출 시 리뷰 수와 평균 평점이 초기화된다")
    void reset_clears_rating_state() {
        ProductRatingEntity rating = new ProductRatingEntity(UUID.randomUUID());

        rating.updateRating(5);
        rating.updateRating(4);

        rating.reset();

        assertThat(rating.getReviewCount()).isZero();
        assertThat(rating.getAvgRating()).isEqualTo(0.0);
    }
}
