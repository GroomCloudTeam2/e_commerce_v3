package com.groom.product.review.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.groom.e_commerce.review.domain.entity.ProductRatingEntity;
import com.groom.e_commerce.review.domain.entity.ReviewEntity;
import com.groom.e_commerce.review.domain.repository.ProductRatingRepository;
import com.groom.e_commerce.review.domain.repository.ReviewRepository;
import com.groom.e_commerce.review.infrastructure.redis.ReviewReadModel;
import com.groom.e_commerce.review.infrastructure.redis.ReviewRedisRepository;
import com.groom.e_commerce.review.presentation.dto.response.ProductReviewResponse;

@ExtendWith(MockitoExtension.class)
class ReviewQueryServiceTest {

    @Mock
    private ReviewRedisRepository reviewRedisRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRatingRepository productRatingRepository;

    @InjectMocks
    private ReviewQueryService service;

    @Test
    @DisplayName("Redis HIT 시 DB 조회 없이 캐시 기반 응답을 반환한다")
    void getProductReviews_cache_hit() {
        // given
        UUID productId = UUID.randomUUID();

        ReviewReadModel readModel = ReviewReadModel.builder()
            .reviewId(UUID.randomUUID())
            .productId(productId)
            .userId(UUID.randomUUID())
            .rating(5)
            .content("색감이 이쁘다")
            .createdAt(LocalDateTime.now())
            .build();

        when(reviewRedisRepository.findByProductId(productId, 0, 10))
            .thenReturn(List.of(readModel));

        ProductRatingEntity rating = new ProductRatingEntity(productId);
        rating.updateRating(5);

        when(productRatingRepository.findByProductId(productId))
            .thenReturn(Optional.of(rating));

        // when
        ProductReviewResponse response =
            service.getProductReviews(productId, 0, 10);

        // then
        verify(reviewRepository, never()).findAllByProductId(any(), any());
        assertThat(response.getReviews()).hasSize(1);
        assertThat(response.getAvgRating()).isEqualTo(5.0);
        assertThat(response.getReviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis MISS 시 DB 조회 후 Redis에 저장하고 응답을 반환한다")
    void getProductReviews_cache_miss() {
        // given
        UUID productId = UUID.randomUUID();

        when(reviewRedisRepository.findByProductId(productId, 0, 10))
            .thenReturn(List.of());

        ReviewEntity review = mock(ReviewEntity.class);
        when(review.getReviewId()).thenReturn(UUID.randomUUID());
        when(review.getProductId()).thenReturn(productId);
        when(review.getUserId()).thenReturn(UUID.randomUUID());
        when(review.getRating()).thenReturn(4);
        when(review.getContent()).thenReturn("색감이 이쁘다");
        when(review.getCreatedAt()).thenReturn(LocalDateTime.now());

        Page<ReviewEntity> page =
            new PageImpl<>(List.of(review), PageRequest.of(0, 10), 1);

        when(reviewRepository.findAllByProductId(eq(productId), any()))
            .thenReturn(page);

        when(productRatingRepository.findByProductId(productId))
            .thenReturn(Optional.empty());

        // when
        ProductReviewResponse response =
            service.getProductReviews(productId, 0, 10);

        // then
        verify(reviewRedisRepository).save(any(ReviewReadModel.class));
        assertThat(response.getReviews()).hasSize(1);
        assertThat(response.getPagination()).isNotNull();
        assertThat(response.getPagination().getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("상품 평점 정보가 없어도 기본값으로 응답한다")
    void getProductReviews_rating_not_exists() {
        // given
        UUID productId = UUID.randomUUID();

        when(reviewRedisRepository.findByProductId(productId, 0, 10))
            .thenReturn(List.of());

        when(reviewRepository.findAllByProductId(eq(productId), any()))
            .thenReturn(Page.empty());

        when(productRatingRepository.findByProductId(productId))
            .thenReturn(Optional.empty());

        // when
        ProductReviewResponse response =
            service.getProductReviews(productId, 0, 10);

        // then
        assertThat(response.getAvgRating()).isEqualTo(0.0);
        assertThat(response.getReviewCount()).isEqualTo(0);
        assertThat(response.getReviews()).isEmpty();
    }
}
