package com.groom.product.review.application.service.intergration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.groom.e_commerce.review.application.service.ProductRatingRebuildService;
import com.groom.e_commerce.review.domain.entity.ProductRatingEntity;
import com.groom.e_commerce.review.domain.entity.ReviewCategory;
import com.groom.e_commerce.review.domain.entity.ReviewEntity;
import com.groom.e_commerce.review.domain.repository.ProductRatingRepository;
import com.groom.e_commerce.review.domain.repository.ReviewRepository;

@SpringBootTest
@Tag("integration")
class ProductRatingRebuildServiceIntegrationTest {

    @Autowired
    private ProductRatingRebuildService rebuildService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRatingRepository productRatingRepository;

    @Test
    @Transactional
    void 리뷰_기반으로_상품평점이_정상적으로_재계산된다() {
        // given
        UUID productId = UUID.randomUUID();

        reviewRepository.save(
            ReviewEntity.builder()
                .orderId(UUID.randomUUID())
                .productId(productId)
                .userId(UUID.randomUUID())
                .rating(4)
                .content("괜찮아요")
                .category(ReviewCategory.PERFORMANCE)
                .build()
        );

        reviewRepository.save(
            ReviewEntity.builder()
                .orderId(UUID.randomUUID())
                .productId(productId)
                .userId(UUID.randomUUID())
                .rating(5)
                .content("아주 좋아요")
                .category(ReviewCategory.QUALITY)
                .build()
        );


        // when
        rebuildService.rebuild(productId);

        // then
        ProductRatingEntity rating =
            productRatingRepository.findByProductId(productId).orElseThrow();

        assertThat(rating.getReviewCount()).isEqualTo(2);
        assertThat(rating.getAvgRating()).isEqualTo(4.5);
    }
}
