package com.groom.product.review.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groom.e_commerce.global.infrastructure.client.Classification.AiClient;
import com.groom.e_commerce.review.application.event.ReviewCreatedEvent;
import com.groom.e_commerce.review.application.validator.OrderReviewValidator;
import com.groom.e_commerce.review.domain.entity.ProductRatingEntity;
import com.groom.e_commerce.review.domain.entity.ReviewCategory;
import com.groom.e_commerce.review.domain.entity.ReviewEntity;
import com.groom.e_commerce.review.domain.entity.ReviewLikeEntity;
import com.groom.e_commerce.review.domain.repository.ProductRatingRepository;
import com.groom.e_commerce.review.domain.repository.ReviewLikeRepository;
import com.groom.e_commerce.review.domain.repository.ReviewRepository;
import com.groom.e_commerce.review.presentation.dto.request.CreateReviewRequest;
import com.groom.e_commerce.review.presentation.dto.request.UpdateReviewRequest;
import com.groom.e_commerce.review.presentation.dto.response.ReviewResponse;
import com.groom.e_commerce.user.domain.entity.user.UserRole;
import org.springframework.context.ApplicationEventPublisher;


@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewLikeRepository reviewLikeRepository;

    @Mock
    private ProductRatingRepository productRatingRepository;

    @Mock
    private AiClient aiClient;

    @Mock
    private OrderReviewValidator orderReviewValidator;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    @DisplayName("리뷰 작성 시 AI 분류 후 저장하고 이벤트를 발행한다")
    void createReview_success() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CreateReviewRequest request =
            new CreateReviewRequest(5, "좋아요");

        when(aiClient.classify(anyString()))
            .thenReturn(ReviewCategory.DESIGN);

        ReviewResponse response =
            reviewService.createReview(orderId, productId, userId, request);

        verify(orderReviewValidator)
            .validate(orderId, productId, userId);

        verify(reviewRepository).save(any(ReviewEntity.class));
        verify(applicationEventPublisher)
            .publishEvent(any(ReviewCreatedEvent.class));

        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getCategory()).isEqualTo(ReviewCategory.DESIGN);
    }

    @Test
    @DisplayName("50자 초과 리뷰는 AI 호출 없이 ERR 카테고리로 분류된다")
    void createReview_long_comment_sets_ERR_category() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        String longComment = "a".repeat(60);
        CreateReviewRequest request =
            new CreateReviewRequest(4, longComment);

        ReviewResponse response =
            reviewService.createReview(orderId, productId, userId, request);

        verify(aiClient, never()).classify(anyString());
        assertThat(response.getCategory()).isEqualTo(ReviewCategory.ERR);
    }

    @Test
    @DisplayName("리뷰 평점 변경 시 상품 평점도 함께 재계산된다")
    void updateReview_rating_changed() {
        UUID reviewId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ReviewEntity review = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(productId)
            .userId(userId)
            .rating(3)
            .content("기존 리뷰")
            .category(ReviewCategory.PRICE)
            .build();

        ProductRatingEntity rating = new ProductRatingEntity(productId);
        rating.updateRating(3);

        when(reviewRepository.findById(reviewId))
            .thenReturn(Optional.of(review));
        when(productRatingRepository.findByProductId(productId))
            .thenReturn(Optional.of(rating));
        when(aiClient.classify(anyString()))
            .thenReturn(ReviewCategory.PRICE);

        UpdateReviewRequest request =
            new UpdateReviewRequest("너무 비싸다", 1);

        ReviewResponse response =
            reviewService.updateReview(reviewId, userId, request);

        assertThat(response.getRating()).isEqualTo(1);
        assertThat(rating.getReviewCount()).isEqualTo(1);
        assertThat(rating.getAvgRating()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("리뷰 수정 시 작성자가 아니면 예외가 발생한다")
    void updateReview_fail_not_owner() {
        UUID reviewId = UUID.randomUUID();

        ReviewEntity review = mock(ReviewEntity.class);
        when(review.getUserId()).thenReturn(UUID.randomUUID());

        when(reviewRepository.findById(reviewId))
            .thenReturn(Optional.of(review));

        assertThatThrownBy(() ->
            reviewService.updateReview(
                reviewId,
                UUID.randomUUID(),
                new UpdateReviewRequest("수정", 3)
            )
        ).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("작성자는 리뷰를 삭제할 수 있다")
    void deleteReview_owner_success() {
        UUID reviewId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        ReviewEntity review = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(productId)
            .userId(userId)
            .rating(4)
            .content("삭제 테스트")
            .category(ReviewCategory.QUALITY)
            .build();

        ProductRatingEntity rating = new ProductRatingEntity(productId);
        rating.updateRating(4);

        when(reviewRepository.findById(reviewId))
            .thenReturn(Optional.of(review));
        when(productRatingRepository.findByProductId(productId))
            .thenReturn(Optional.of(rating));

        reviewService.deleteReview(reviewId, userId, UserRole.USER);

        assertThat(rating.getReviewCount()).isZero();
        assertThat(rating.getAvgRating()).isZero();
    }

    @Test
    @DisplayName("리뷰 좋아요 성공")
    void likeReview_success() {
        UUID reviewId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ReviewEntity review = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .rating(3)
            .content("테스트 리뷰")
            .category(ReviewCategory.DESIGN)
            .build();

        when(reviewRepository.findById(reviewId))
            .thenReturn(Optional.of(review));
        when(reviewLikeRepository.findByReviewIdAndUserId(reviewId, userId))
            .thenReturn(Optional.empty());

        int count = reviewService.likeReview(reviewId, userId);

        verify(reviewLikeRepository).save(any(ReviewLikeEntity.class));
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("리뷰 좋아요 취소 성공")
    void unlikeReview_success() {
        UUID reviewId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ReviewEntity review = ReviewEntity.builder()
            .orderId(UUID.randomUUID())
            .productId(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .rating(3)
            .content("테스트 리뷰")
            .category(ReviewCategory.DESIGN)
            .build();

        ReviewLikeEntity like =
            new ReviewLikeEntity(reviewId, userId);

        when(reviewRepository.findById(reviewId))
            .thenReturn(Optional.of(review));
        when(reviewLikeRepository.findByReviewIdAndUserId(reviewId, userId))
            .thenReturn(Optional.of(like));

        int count = reviewService.unlikeReview(reviewId, userId);

        verify(reviewLikeRepository).delete(like);
        assertThat(count).isEqualTo(0);
    }
}
