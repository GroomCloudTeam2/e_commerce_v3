package com.groom.product.review.application.event.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import com.groom.product.review.application.event.ReviewCreatedEvent;
import com.groom.product.review.domain.entity.ProductRatingEntity;
import com.groom.product.review.domain.repository.ProductRatingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class ProductRatingEventListenerTest {

    @Mock
    private ProductRatingRepository productRatingRepository;

    @InjectMocks
    private ProductRatingEventListener listener;

    @Test
    @DisplayName("상품 평점이 이미 존재하면 기존 엔티티를 업데이트한다")
    void handle_existing_rating() {
        // given
        UUID productId = UUID.randomUUID();

        ReviewCreatedEvent event = new ReviewCreatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            productId,
            4
        );

        ProductRatingEntity existingRating =
            new ProductRatingEntity(productId);

        existingRating.updateRating(3); // 기존 리뷰 1개, 평균 3.0

        when(productRatingRepository.findByProductId(any()))
            .thenReturn(Optional.of(existingRating));

        // when
        listener.handle(event);

        // then
        ArgumentCaptor<ProductRatingEntity> captor =
            ArgumentCaptor.forClass(ProductRatingEntity.class);

        verify(productRatingRepository).save(captor.capture());

        ProductRatingEntity saved = captor.getValue();

        assertThat(saved.getProductId()).isEqualTo(productId);
        assertThat(saved.getReviewCount()).isEqualTo(2);
        assertThat(saved.getAvgRating()).isEqualTo(3.5);
    }

    @Test
    @DisplayName("상품 평점이 없으면 새 엔티티를 생성해서 저장한다")
    void handle_new_rating() {
        // given
        UUID productId = UUID.randomUUID();

        ReviewCreatedEvent event = new ReviewCreatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            productId,
            5
        );

        when(productRatingRepository.findByProductId(any()))
            .thenReturn(Optional.empty());

        // when
        listener.handle(event);

        // then
        ArgumentCaptor<ProductRatingEntity> captor =
            ArgumentCaptor.forClass(ProductRatingEntity.class);

        verify(productRatingRepository).save(captor.capture());

        ProductRatingEntity saved = captor.getValue();

        assertThat(saved.getProductId()).isEqualTo(productId);
        assertThat(saved.getReviewCount()).isEqualTo(1);
        assertThat(saved.getAvgRating()).isEqualTo(5.0);
    }
}
