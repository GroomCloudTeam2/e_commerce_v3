package com.groom.cart.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groom.cart.application.dto.ProductCartInfo;
import com.groom.cart.application.dto.StockManagement;
import com.groom.cart.application.event.request.CartCheckoutRequestedEvent;
import com.groom.cart.domain.model.CartItem;
import com.groom.cart.domain.model.CartItemKey;
import com.groom.cart.domain.repository.CartRepository;
import com.groom.cart.infrastructure.feign.ProductClient;
import com.groom.cart.presentation.dto.request.CartAddRequest;
import com.groom.cart.presentation.dto.response.CartItemResponse;
import com.groom.common.presentation.advice.CustomException;
import com.groom.common.presentation.advice.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 장바구니 서비스
 *
 * 책임:
 * - 상품 서비스 동기 검증
 * - 재고 검증
 * - 장바구니 유스케이스 오케스트레이션
 * 책임 아님:
 * - Redis Key/Hash 구조
 * - 수량 증가 방식 (HINCRBY)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

	private final CartRepository cartRepository;
	private final ProductClient productClient;
	private final ApplicationEventPublisher eventPublisher;


	/**
	 * 장바구니 상품 추가
	 * 정책:
	 *
	 * - 판매 가능 여부만 검증
	 * - 재고 가점유 ❌
	 * - 수량 증가는 Redis 원자 연산(HINCRBY)
	 */
	public void addItemToCart(UUID userId, CartAddRequest request) {

		log.info("장바구니 추가 요청 - userId={}, productId={}, variantId={}, quantity={}",
			userId, request.getProductId(), request.getVariantId(), request.getQuantity());

		// 1️⃣ 상품 정보 검증 (동기)
		StockManagement stock = StockManagement.of(
			request.getProductId(),
			request.getVariantId(),
			request.getQuantity()
		);

		ProductCartInfo product = productClient
			.getProductCartInfos(List.of(stock))
			.stream()
			.findFirst()
			.orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

		if (!product.isAvailable()) {
			throw new CustomException(ErrorCode.PRODUCT_NOT_ON_SALE);
		}

		// 2️⃣ 현재 장바구니 수량 조회
		int existingQty = cartRepository
			.findItem(userId, request.getProductId(), request.getVariantId())
			.map(CartItem::getQuantity)
			.orElse(0);

		int newTotalQty = existingQty + request.getQuantity();

		// 3️⃣ 재고 검증
		if (product.getStockQuantity() < newTotalQty) {
			throw new CustomException(ErrorCode.STOCK_NOT_ENOUGH);
		}

		// 4️⃣ Redis에 수량 증가 요청 (원자적)
		cartRepository.addItem(
			userId,
			request.getProductId(),
			request.getVariantId(),
			request.getQuantity()
		);

		log.info("장바구니 추가 완료 - userId={}, productId={}",
			userId, request.getProductId());
	}

	/**
	 * 내 장바구니 조회
	 * 흐름:
	 * 1. Redis에서 장바구니 아이템 조회
	 * 2. Product Service Bulk 조회
	 * 3. API Composition
	 */
	@Transactional(readOnly = true)
	public List<CartItemResponse> getMyCart(UUID userId) {

		log.info("장바구니 조회 - userId={}", userId);

		// 1️⃣ Redis 조회
		List<CartItem> cartItems = cartRepository.findAll(userId);
		if (cartItems.isEmpty()) {
			return List.of();
		}

		// 2️⃣ 상품 정보 Bulk 조회
		List<StockManagement> stockInfos = cartItems.stream()
			.map(CartItem::toStockManagement)
			.toList();

		List<ProductCartInfo> productInfos =
			productClient.getProductCartInfos(stockInfos);

		// 3️⃣ 빠른 조회를 위한 Map 구성
		Map<CartItemKey, ProductCartInfo> productInfoMap =
			productInfos.stream()
				.collect(Collectors.toMap(
					info -> new CartItemKey(info.getProductId(), info.getVariantId()),
					Function.identity()
				));

		// 4️⃣ 응답 조합
		List<CartItemResponse> responses = cartItems.stream()
			.map(item -> {
				CartItemKey key = new CartItemKey(
					item.getProductId(),
					item.getVariantId()
				);

				ProductCartInfo info = productInfoMap.get(key);
				if (info == null) {
					log.warn("삭제되었거나 조회 불가 상품 - productId={}, variantId={}",
						item.getProductId(), item.getVariantId());
					return null;
				}

				return CartItemResponse.builder()
					.productId(item.getProductId())
					.variantId(item.getVariantId())
					.productName(info.getProductName())
					.optionName(info.getOptionName())
					.thumbnailUrl(info.getThumbnailUrl())
					.price(info.getPrice())
					.quantity(item.getQuantity())
					.totalPrice(info.getPrice() * item.getQuantity())
					.stockQuantity(info.getStockQuantity())
					.isAvailable(info.isAvailable())
					.build();
			})
			.filter(Objects::nonNull)
			.toList();

		log.info("장바구니 조회 완료 - userId={}, itemCount={}",
			userId, responses.size());

		return responses;
	}

	/**
	 * 장바구니 수량 변경 (덮어쓰기)
	 */
	public void updateItemQuantity(
		UUID userId,
		UUID productId,
		UUID variantId,
		int quantity
	) {

		log.info("장바구니 수량 변경 - userId={}, productId={}, variantId={}, quantity={}",
			userId, productId, variantId, quantity);

		CartItem item = cartRepository
			.findItem(userId, productId, variantId)
			.orElseThrow(() -> new CustomException(ErrorCode.CART_ITEM_NOT_FOUND));

		// productClient 사용 (Feign)
		ProductCartInfo product = productClient
			.getProductCartInfos(List.of(item.toStockManagement()))
			.stream()
			.findFirst()
			.orElseThrow(() -> new CustomException(ErrorCode.PRODUCT_NOT_FOUND));

		if (!product.isAvailable()) {
			throw new CustomException(ErrorCode.PRODUCT_NOT_ON_SALE);
		}

		if (product.getStockQuantity() < quantity) {
			throw new CustomException(ErrorCode.STOCK_NOT_ENOUGH);
		}

		cartRepository.updateQuantity(userId, productId, variantId, quantity);

		log.info("장바구니 수량 변경 완료 - userId={}, productId={}",
			userId, productId);
	}


	public UUID checkout(UUID userId) {

		List<CartItem> cartItems = cartRepository.findAll(userId);

		if (cartItems.isEmpty()) {
			throw new CustomException(ErrorCode.CART_EMPTY);
		}

		UUID orderId = UUID.randomUUID();

		List<CartCheckoutRequestedEvent.CartOrderItem> items =
			cartItems.stream()
				.map(item -> new CartCheckoutRequestedEvent.CartOrderItem(
					item.getProductId(),
					item.getVariantId(),
					item.getQuantity()
				))
				.toList();

		eventPublisher.publishEvent(
			new CartCheckoutRequestedEvent(userId, orderId, items)
		);

		return orderId;
	}


	public UUID checkout(UUID userId, List<CartItemKey> selectedItems) {

		List<CartItem> cartItems = cartRepository.findAll(userId);

		List<CartItem> targetItems = cartItems.stream()
			.filter(item ->
				selectedItems.contains(
					new CartItemKey(item.getProductId(), item.getVariantId())
				)
			)
			.toList();

		if (targetItems.isEmpty()) {
			throw new CustomException(ErrorCode.CART_EMPTY);
		}

		UUID orderId = UUID.randomUUID();

		List<CartCheckoutRequestedEvent.CartOrderItem> items =
			targetItems.stream()
				.map(item -> new CartCheckoutRequestedEvent.CartOrderItem(
					item.getProductId(),
					item.getVariantId(),
					item.getQuantity()
				))
				.toList();

		eventPublisher.publishEvent(
			new CartCheckoutRequestedEvent(userId, orderId, items)
		);

		return orderId;
	}



	/**
	 * 장바구니 아이템 단일 삭제
	 */
	public void deleteCartItem(UUID userId, UUID productId, UUID variantId) {

		cartRepository.findItem(userId, productId, variantId)
			.orElseThrow(() -> new CustomException(ErrorCode.CART_ITEM_NOT_FOUND));

		cartRepository.removeItem(userId, productId, variantId);

		log.info("장바구니 아이템 삭제 - userId={}, productId={}",
			userId, productId);
	}

	/**
	 * 장바구니 아이템 일괄 삭제
	 */
	public void removeCartItems(UUID userId, List<CartItemDeleteRequest> items) {

		if (items == null || items.isEmpty()) {
			return;
		}

		List<CartItemKey> keys = items.stream()
			.map(i -> new CartItemKey(i.getProductId(), i.getVariantId()))
			.toList();

		cartRepository.removeItems(userId, keys);

		log.info("장바구니 아이템 일괄 삭제 - userId={}, count={}",
			userId, items.size());
	}

	/**
	 * 장바구니 전체 비우기
	 * (주문 확정 이벤트 등에서 사용)
	 */
	public void clearCart(UUID userId) {
		cartRepository.clear(userId);
		log.info("장바구니 전체 삭제 - userId={}", userId);
	}

	/**
	 * 일괄 삭제 요청 DTO
	 * 바꿨는데.
	 */
	@lombok.Getter
	@lombok.NoArgsConstructor
	@lombok.AllArgsConstructor
	public static class CartItemDeleteRequest {
		private UUID productId;
		private UUID variantId;
	}
}
