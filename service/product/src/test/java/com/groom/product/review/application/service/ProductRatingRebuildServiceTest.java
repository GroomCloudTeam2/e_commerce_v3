package com.groom.product.review.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.groom.product.review.domain.entity.ProductRatingEntity;
import com.groom.product.review.domain.entity.ReviewEntity;
import com.groom.product.review.domain.repository.ProductRatingRepository;
import com.groom.product.review.domain.repository.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;



@ExtendWith(MockitoExtension.class)
class ProductRatingRebuildServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRatingRepository productRatingRepository;

    @InjectMocks
    private ProductRatingRebuildService rebuildService;
    @Test
    @DisplayName("기존 상품 평점이 있으면 reset 후 모든 리뷰를 기반으로 재계산한다")
    void rebuild_existing_rating() {
        // given
        UUID productId = UUID.randomUUID();

        ReviewEntity review1 = mock(ReviewEntity.class);
        ReviewEntity review2 = mock(ReviewEntity.class);

        when(review1.getRating()).thenReturn(4);
        when(review2.getRating()).thenReturn(5);

        when(reviewRepository.findAllByProductIdForRebuild(productId))
            .thenReturn(List.of(review1, review2));

        ProductRatingEntity existingRating = new ProductRatingEntity(productId);
        existingRating.updateRating(3); // 기존 데이터 (reset 되므로 의미 없음)

        when(productRatingRepository.findByProductId(productId))
            .thenReturn(Optional.of(existingRating));

        // when
        rebuildService.rebuild(productId);

        // then
        ArgumentCaptor<ProductRatingEntity> captor =
            ArgumentCaptor.forClass(ProductRatingEntity.class);

        verify(productRatingRepository).save(captor.capture());

        ProductRatingEntity saved = captor.getValue();
        assertThat(saved.getReviewCount()).isEqualTo(2);
        assertThat(saved.getAvgRating()).isEqualTo(4.5);
    }


    @Test
    @DisplayName("상품 평점이 없으면 새 엔티티를 생성해서 리뷰 기반으로 계산한다")
    void rebuild_new_rating() {
        // given
        UUID productId = UUID.randomUUID();

        ReviewEntity review = mock(ReviewEntity.class);
        when(review.getRating()).thenReturn(5);

        when(reviewRepository.findAllByProductIdForRebuild(productId))
            .thenReturn(List.of(review));

        when(productRatingRepository.findByProductId(productId))
            .thenReturn(Optional.empty());

        // when
        rebuildService.rebuild(productId);

        // then
        ArgumentCaptor<ProductRatingEntity> captor =
            ArgumentCaptor.forClass(ProductRatingEntity.class);

        verify(productRatingRepository).save(captor.capture());

        ProductRatingEntity saved = captor.getValue();
        assertThat(saved.getReviewCount()).isEqualTo(1);
        assertThat(saved.getAvgRating()).isEqualTo(5.0);

    }

    @Test
    @DisplayName("리뷰가 하나도 없으면 reset 후 0점 상태로 저장된다")
    void rebuild_no_reviews() {
        // given
        UUID productId = UUID.randomUUID();

        when(reviewRepository.findAllByProductIdForRebuild(productId))
            .thenReturn(List.of());

        when(productRatingRepository.findByProductId(productId))
            .thenReturn(Optional.empty());

        // when
        rebuildService.rebuild(productId);

        // then
        ArgumentCaptor<ProductRatingEntity> captor =
            ArgumentCaptor.forClass(ProductRatingEntity.class);

        verify(productRatingRepository).save(captor.capture());

        ProductRatingEntity saved = captor.getValue();
        assertThat(saved.getReviewCount()).isZero();
        assertThat(saved.getAvgRating()).isZero();
    }
}
