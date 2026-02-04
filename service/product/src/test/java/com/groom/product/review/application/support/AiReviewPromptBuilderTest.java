package com.groom.product.review.application.support;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.groom.e_commerce.review.domain.entity.ReviewCategory;
import com.groom.e_commerce.review.domain.entity.ReviewEntity;

class AiReviewPromptBuilderTest {

    private final AiReviewPromptBuilder builder = new AiReviewPromptBuilder();

    @Test
    @DisplayName("상품명과 카테고리별 리뷰가 포함된 프롬프트가 생성된다")
    void build_contains_product_title_and_reviews() {
        // given
        String productTitle = "맥북 프로";

        ReviewEntity performanceReview = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .rating(5)
            .content("성능이 아주 좋아요")
            .category(ReviewCategory.PERFORMANCE)
            .build();

        ReviewEntity qualityReview = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .rating(4)
            .content("품질이 좋아요")
            .category(ReviewCategory.QUALITY)
            .build();

        Map<ReviewCategory, List<ReviewEntity>> reviews = Map.of(
            ReviewCategory.PERFORMANCE, List.of(performanceReview),
            ReviewCategory.QUALITY, List.of(qualityReview)
        );

        // when
        String prompt = builder.build(productTitle, reviews);

        // then
        assertThat(prompt).contains(productTitle);

        assertThat(prompt).contains("[PERFORMANCE]");
        assertThat(prompt).contains("성능이 아주 좋아요");

        assertThat(prompt).contains("[QUALITY]");
        assertThat(prompt).contains("품질이 좋아요");
    }

    @Test
    @DisplayName("리뷰가 없는 카테고리도 헤더는 포함된다")
    void build_includes_category_header_even_if_empty() {
        // given
        String productTitle = "아이폰";

        Map<ReviewCategory, List<ReviewEntity>> reviews = Map.of(
            ReviewCategory.PRICE, List.of()
        );

        // when
        String prompt = builder.build(productTitle, reviews);

        // then
        assertThat(prompt).contains("[PRICE]");
        assertThat(prompt).doesNotContain("- ");
    }

    @Test
    @DisplayName("프롬프트는 JSON 형식 안내 문구를 포함한다")
    void build_contains_json_format_instruction() {
        // given
        String productTitle = "갤럭시";

        Map<ReviewCategory, List<ReviewEntity>> reviews = Map.of();

        // when
        String prompt = builder.build(productTitle, reviews);

        // then
        assertThat(prompt)
            .contains("\"DELIVERY\"")
            .contains("\"QUALITY\"")
            .contains("\"PRICE\"")
            .contains("\"DESIGN\"")
            .contains("\"ETC\"");
    }
}
