package com.groom.product.product.presentation.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groom.product.product.application.dto.ProductCartInfo;
import com.groom.product.product.application.dto.StockManagement;
import com.groom.product.product.application.service.ProductServiceV1;
import com.groom.product.product.infrastructure.cache.StockRedisService;
import com.groom.product.product.presentation.dto.response.ResProductBulkInfoDto;
import com.groom.product.product.presentation.dto.response.ResStockAvailabilityDto;
import com.groom.product.product.presentation.dto.response.ResStockOperationDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Product Internal API Controller
 * MSA 환경에서 다른 서비스(Order, Cart)가 OpenFeign을 통해 호출하는 내부 API
 *
 * 보안: API Gateway에서 내부 서비스 간 통신만 허용하도록 설정 필요
 *
 * [Request Body 스펙]
 * Order/Cart 담당자는 spec 패키지의 RequestSpec 클래스를 참고하여 Request DTO를 정의
 */
@Slf4j
@Tag(name = "Product Internal", description = "상품 내부 API (서비스 간 통신용)")
@RestController
@RequestMapping("/api/v1/internal/products")
@RequiredArgsConstructor
public class ProductInternalController {

	private final ProductServiceV1 productService;
	private final StockRedisService stockRedisService;

	// ==================== 재고 관리 API ====================

	/**
	 * 재고 가점유 (Reserve)
	 * - 호출 시점: 주문서 생성 시 (Order 서비스)
	 * - 동작: Redis에서 원자적으로 재고 검증 + 차감 + 주문-상품 매핑 저장
	 *
	 * Request Body:
	 * {
	 * "orderId": "UUID",
	 * "items": [{ "productId": "UUID", "variantId": "UUID (optional)", "quantity":
	 * int }]
	 * }
	 */
	@Operation(summary = "재고 가점유", description = "주문 생성 시 재고를 가점유합니다. (Redis Lua Script)")
	@PostMapping("/stock/reserve")
	public ResponseEntity<ResStockOperationDto> reserveStock(
			@RequestBody @jakarta.validation.Valid com.groom.product.product.presentation.dto.request.ReserveStockRequest request) {
		UUID orderId = request.getOrderId();
		List<com.groom.product.product.presentation.dto.request.ReserveStockItem> items = request.getItems();

		log.info("[Internal API] 재고 가점유 요청 - orderId: {}, items: {}", orderId, items.size());

		List<StockManagement> stockManagements = items.stream()
				.filter(item -> item.getProductId() != null)
				.map(item -> StockManagement.of(
						item.getProductId(),
						item.getVariantId(),
						item.getQuantity()))
				.toList();

		// 1. 재고 가점유
		productService.reserveStockBulk(stockManagements);

		// 2. 주문-상품 매핑 저장 (이벤트 수신 시 사용)
		stockRedisService.saveOrderStockItems(orderId, stockManagements);

		log.info("[Internal API] 재고 가점유 완료 - orderId: {}", orderId);
		return ResponseEntity.ok(ResStockOperationDto.success("재고 가점유가 완료되었습니다."));
	}

	/**
	 * 단일 아이템 재고 가점유 (Reserve)
	 * - 호출 시점: 주문서 생성 시 개별 아이템 (Order 서비스)
	 * - 동작: Redis에서 원자적으로 재고 검증 + 차감 + 주문-상품 매핑 저장
	 */
	@Operation(summary = "단일 아이템 재고 가점유", description = "개별 상품의 재고를 가점유합니다.")
	@PostMapping("/stock/reserve-single")
	public ResponseEntity<ResStockOperationDto> reserveStockSingle(
			@RequestBody @jakarta.validation.Valid com.groom.product.product.presentation.dto.request.ReserveStockSingleRequest request) {
		UUID orderId = request.getOrderId();
		UUID productId = request.getProductId();
		UUID variantId = request.getVariantId();
		int quantity = request.getQuantity();

		log.info("[Internal API] 단일 재고 가점유 요청 - orderId: {}, productId: {}, variantId: {}, quantity: {}", orderId,
				productId, variantId, quantity);

		StockManagement stockManagement = StockManagement.of(productId, variantId, quantity);

		// 1. 재고 가점유
		productService.reserveStockBulk(List.of(stockManagement));

		// 2. 주문-상품 매핑 저장 (기존 매핑이 있으면 추가)
		List<StockManagement> existingItems = new java.util.ArrayList<>(stockRedisService.getOrderStockItems(orderId));
		existingItems.add(stockManagement);
		stockRedisService.saveOrderStockItems(orderId, existingItems);

		log.info("[Internal API] 단일 재고 가점유 완료 - orderId: {}, productId: {}", orderId, productId);
		return ResponseEntity.ok(ResStockOperationDto.success("재고 가점유가 완료되었습니다."));
	}

	/**
	 * 가용 재고 조회
	 * - 호출 시점: 재고 확인이 필요할 때
	 * - 동작: Redis에서 현재 가용 재고 조회
	 */
	@Operation(summary = "가용 재고 조회", description = "Redis에서 현재 가용 재고를 조회합니다.")
	@GetMapping("/{productId}/stock")
	public ResponseEntity<ResStockAvailabilityDto> getAvailableStock(
			@PathVariable UUID productId) {
		Integer stock = productService.getAvailableStock(productId, null);
		return ResponseEntity.ok(ResStockAvailabilityDto.of(productId, null, stock));
	}

	/**
	 * Variant 가용 재고 조회
	 */
	@Operation(summary = "Variant 가용 재고 조회", description = "옵션 상품의 가용 재고를 조회합니다.")
	@GetMapping("/{productId}/variants/{variantId}/stock")
	public ResponseEntity<ResStockAvailabilityDto> getVariantAvailableStock(
			@PathVariable UUID productId,
			@PathVariable UUID variantId) {
		Integer stock = productService.getAvailableStock(productId, variantId);
		return ResponseEntity.ok(ResStockAvailabilityDto.of(productId, variantId, stock));
	}

	// ==================== 상품 정보 조회 API ====================

	/**
	 * 상품 정보 벌크 조회
	 * - 호출 시점: 주문 생성, 장바구니 조회 시 (Order/Cart 서비스)
	 * - 동작: N+1 방지를 위한 벌크 조회
	 */
	@Operation(summary = "상품 정보 벌크 조회", description = "여러 상품 정보를 한 번에 조회합니다. (N+1 방지)")
	@PostMapping("/bulk-info")
	public ResponseEntity<List<ProductCartInfo>> getProductBulkInfo(
			@RequestBody List<StockManagement> items) {
		log.info("[Internal API] 상품 정보 벌크 조회 요청 - items: {}", items.size());

		List<ProductCartInfo> productInfos = productService.getProductCartInfos(items);

		log.info("[Internal API] 상품 정보 벌크 조회 완료 - results: {}", productInfos.size());
		return ResponseEntity.ok(productInfos);
	}
}