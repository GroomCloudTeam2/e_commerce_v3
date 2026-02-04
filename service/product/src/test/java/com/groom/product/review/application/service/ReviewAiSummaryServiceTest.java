package com.groom.product.review.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groom.e_commerce.global.infrastructure.client.OpenAi.OpenAiClient;
import com.groom.e_commerce.product.domain.entity.Product;
import com.groom.e_commerce.product.domain.repository.ProductRepository;
import com.groom.e_commerce.review.application.support.AiReviewPromptBuilder;
import com.groom.e_commerce.review.domain.entity.ProductRatingEntity;
import com.groom.e_commerce.review.domain.entity.ReviewCategory;
import com.groom.e_commerce.review.domain.entity.ReviewEntity;
import com.groom.e_commerce.review.domain.repository.ProductRatingRepository;
import com.groom.e_commerce.review.domain.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewAiSummaryServiceTest {

	@Mock
	private ReviewRepository reviewRepository;

	@Mock
	private ProductRatingRepository productRatingRepository;

	@Mock
	private AiReviewPromptBuilder promptBuilder;

	@Mock
	private OpenAiClient openAiClient;

	@Mock
	private ProductRepository productRepository;

	@InjectMocks
	private ReviewAiSummaryService service;

	@Test
	@DisplayName("AI 리뷰 요약이 정상적으로 생성되어 상품 평점에 저장된다")
	void generate_success() {
		// given
		UUID productId = UUID.randomUUID();

		Product product = mock(Product.class);
		when(product.getTitle()).thenReturn("맥북 프로");

		when(productRepository.findByIdAndNotDeleted(productId))
			.thenReturn(Optional.of(product));

		when(reviewRepository.findTopReviews(any(), any(), any()))
			.thenReturn(List.of(mock(ReviewEntity.class)));

		when(promptBuilder.build(eq("맥북 프로"), any(Map.class)))
			.thenReturn("PROMPT");

		when(openAiClient.summarizeReviews("PROMPT"))
			.thenReturn("AI SUMMARY");

		ProductRatingEntity rating = new ProductRatingEntity(productId);
		when(productRatingRepository.findByProductId(productId))
			.thenReturn(Optional.of(rating));

		// when
		service.generate(productId);

		// then
		ArgumentCaptor<ProductRatingEntity> captor =
			ArgumentCaptor.forClass(ProductRatingEntity.class);

		verify(productRatingRepository).save(captor.capture());

		ProductRatingEntity saved = captor.getValue();
		assertThat(saved.getAiReview()).isEqualTo("AI SUMMARY");
	}

	@Test
	@DisplayName("상품이 존재하지 않으면 예외가 발생한다")
	void generate_fail_product_not_found() {
		// given
		UUID productId = UUID.randomUUID();

		when(productRepository.findByIdAndNotDeleted(productId))
			.thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.generate(productId))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("상품 제목");
	}

	@Test
	@DisplayName("상품 평점 엔티티가 없으면 예외가 발생한다")
	void generate_fail_rating_not_found() {
		// given
		UUID productId = UUID.randomUUID();

		Product product = mock(Product.class);
		when(product.getTitle()).thenReturn("아이폰");

		when(productRepository.findByIdAndNotDeleted(productId))
			.thenReturn(Optional.of(product));

		when(reviewRepository.findTopReviews(any(), any(), any()))
			.thenReturn(List.of());

		when(promptBuilder.build(anyString(), any()))
			.thenReturn("PROMPT");

		when(openAiClient.summarizeReviews(any()))
			.thenReturn("AI SUMMARY");

		when(productRatingRepository.findByProductId(productId))
			.thenReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> service.generate(productId))
			.isInstanceOf(RuntimeException.class);
	}
}
