package com.groom.product.review.application.service;

import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groom.product.review.application.event.ReviewCreatedEvent;
import com.groom.product.review.application.validator.OrderReviewValidator;
import com.groom.product.review.domain.entity.ProductRatingEntity;
import com.groom.product.review.infrastructure.client.Classification.AiClient;
import com.groom.product.review.domain.entity.ReviewCategory;
import com.groom.product.review.domain.entity.ReviewEntity;
import com.groom.product.review.domain.entity.ReviewLikeEntity;
import com.groom.product.review.domain.repository.ProductRatingRepository;
import com.groom.product.review.domain.repository.ReviewLikeRepository;
import com.groom.product.review.domain.repository.ReviewRepository;
import com.groom.product.review.presentation.dto.request.CreateReviewRequest;
import com.groom.product.review.presentation.dto.request.UpdateReviewRequest;
import com.groom.product.review.presentation.dto.response.PaginationResponse;
import com.groom.product.review.presentation.dto.response.ProductReviewResponse;
import com.groom.product.review.presentation.dto.response.ReviewResponse;
import com.groom.common.enums.UserRole;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

	private final ReviewRepository reviewRepository;
	private final ReviewLikeRepository reviewLikeRepository;
	private final ProductRatingRepository productRatingRepository;
	private final AiClient aiClient;
	private final OrderReviewValidator orderReviewValidator;
	private final ApplicationEventPublisher applicationEventPublisher;

	private static final String SORT_CREATED_AT = "createdAt";
	private static final String NO_REVIEW = "리뷰가 존재하지 않습니다.";

	/**
	 * 리뷰 작성
	 */
	@Transactional
	public ReviewResponse createReview(
		UUID orderId,
		UUID productId,
		UUID currentUserId,
		CreateReviewRequest request
	) {
		// 0. 주문/상품/유저 검증
		orderReviewValidator.validate(orderId, productId, currentUserId);

		String comment = request.getContent();
		if (comment == null || comment.isBlank()) {
			throw new IllegalArgumentException("리뷰 내용은 필수입니다.");
		}

		// 1. AI 카테고리 분류
		ReviewCategory category =
			comment.length() > 50
				? ReviewCategory.ERR
				: aiClient.classify(comment);

		// 2. 리뷰 저장
		ReviewEntity review = ReviewEntity.builder()
			.orderId(orderId)
			.productId(productId)
			.userId(currentUserId)
			.rating(request.getRating())
			.content(comment)
			.category(category)
			.build();

		reviewRepository.save(review);

		// 3. 이벤트 발행
		applicationEventPublisher.publishEvent(
			new ReviewCreatedEvent(
				review.getUserId(),
				review.getReviewId(),
				review.getProductId(),
				request.getRating()
			)
		);

		return ReviewResponse.fromEntity(review);
	}

	/**
	 * 리뷰 수정
	 */
	@Transactional
	public ReviewResponse updateReview(
		UUID reviewId,
		UUID currentUserId,
		UpdateReviewRequest request
	) {
		ReviewEntity review = reviewRepository.findById(reviewId)
			.orElseThrow(() -> new IllegalArgumentException(NO_REVIEW));

		if (!review.getUserId().equals(currentUserId)) {
			throw new SecurityException("수정 권한이 없습니다.");
		}

		// 평점 변경
		if (request.getRating() != null &&
			!review.getRating().equals(request.getRating())) {

			ProductRatingEntity ratingEntity =
				productRatingRepository.findByProductId(review.getProductId())
					.orElseThrow(() -> new IllegalStateException("상품 통계 정보가 없습니다."));

			ratingEntity.removeRating(review.getRating());
			ratingEntity.updateRating(request.getRating());

			review.updateRating(request.getRating());
		}

		// 내용 변경 시 AI 재분류
		if (request.getContent() != null && !request.getContent().isBlank()) {
			ReviewCategory category =
				aiClient.classify(request.getContent());

			review.updateContentAndCategory(
				request.getContent(),
				category
			);
		}

		return ReviewResponse.fromEntity(review);
	}

	/**
	 * 리뷰 삭제 (Soft Delete)
	 */
	@Transactional
	public void deleteReview(
		UUID reviewId,
		UUID currentUserId,
		UserRole currentUserRole
	) {
		ReviewEntity review = reviewRepository.findById(reviewId)
			.orElseThrow(() -> new IllegalArgumentException(NO_REVIEW));

		boolean isOwner = review.getUserId().equals(currentUserId);
		boolean isManager =
			currentUserRole == UserRole.MANAGER ||
				currentUserRole == UserRole.MASTER;

		if (!isOwner && !isManager) {
			throw new SecurityException("삭제 권한이 없습니다.");
		}

		ProductRatingEntity ratingEntity =
			productRatingRepository.findByProductId(review.getProductId())
				.orElseThrow(() -> new IllegalStateException("상품 통계 정보가 없습니다."));

		ratingEntity.removeRating(review.getRating());

		review.softDelete(currentUserId.toString());
	}

	/**
	 * 상품별 리뷰 목록 조회
	 */
	public ProductReviewResponse getProductReviews(
		UUID productId,
		int page,
		int size,
		String sortParam
	) {
		Sort sort =
			"like".equalsIgnoreCase(sortParam)
				? Sort.by("likeCount").descending()
				.and(Sort.by(SORT_CREATED_AT).descending())
				: Sort.by(SORT_CREATED_AT).descending();

		Pageable pageable = PageRequest.of(page, size, sort);

		Page<ReviewEntity> reviewPage =
			reviewRepository.findAllByProductId(productId, pageable);

		ProductRatingEntity ratingEntity =
			productRatingRepository.findByProductId(productId)
				.orElseGet(() -> new ProductRatingEntity(productId));

		return ProductReviewResponse.builder()
			.avgRating(ratingEntity.getAvgRating())
			.reviewCount(ratingEntity.getReviewCount())
			.aiReview(ratingEntity.getAiReview())
			.reviews(
				reviewPage.getContent().stream()
					.map(ReviewResponse::fromEntity)
					.collect(Collectors.toList())
			)
			.pagination(
				PaginationResponse.builder()
					.totalElements(reviewPage.getTotalElements())
					.totalPages(reviewPage.getTotalPages())
					.currentPage(reviewPage.getNumber())
					.isLast(reviewPage.isLast())
					.build()
			)
			.build();
	}

	/**
	 * 단건 리뷰 조회
	 */
	public ReviewResponse getReview(UUID reviewId, UUID currentUserId) {
		ReviewEntity review = reviewRepository.findById(reviewId)
			.orElseThrow(() -> new IllegalArgumentException(NO_REVIEW));

		if (!review.getUserId().equals(currentUserId)) {
			throw new SecurityException("조회 권한이 없습니다.");
		}

		return ReviewResponse.fromEntity(review);
	}

	@Transactional(readOnly = true)
	public Page<ReviewResponse> getMyReviews(
		UUID currentUserId,
		Pageable pageable
	) {
		return reviewRepository
			.findByUserId(
				currentUserId,
				PageRequest.of(
					pageable.getPageNumber(),
					pageable.getPageSize(),
					Sort.by(Sort.Direction.DESC, SORT_CREATED_AT)
				)
			)
			.map(ReviewResponse::fromEntity);
	}

	@Transactional
	public int likeReview(UUID reviewId, UUID userId) {
		ReviewEntity review = reviewRepository.findById(reviewId)
			.orElseThrow(() -> new IllegalArgumentException(NO_REVIEW));

		reviewLikeRepository.findByReviewIdAndUserId(reviewId, userId)
			.ifPresent(like -> {
				throw new IllegalStateException("이미 좋아요를 눌렀습니다.");
			});

		reviewLikeRepository.save(
			new ReviewLikeEntity(reviewId, userId)
		);

		review.incrementLikeCount();
		return review.getLikeCount();
	}

	@Transactional
	public int unlikeReview(UUID reviewId, UUID userId) {
		ReviewEntity review = reviewRepository.findById(reviewId)
			.orElseThrow(() -> new IllegalArgumentException(NO_REVIEW));

		ReviewLikeEntity like =
			reviewLikeRepository.findByReviewIdAndUserId(reviewId, userId)
				.orElseThrow(() -> new IllegalStateException("좋아요를 누르지 않은 리뷰입니다."));

		reviewLikeRepository.delete(like);
		review.decrementLikeCount();
		return review.getLikeCount();
	}
}
