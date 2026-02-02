package com.groom.order.presentation.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groom.common.infrastructure.config.security.CustomUserDetails;
import com.groom.order.application.service.OrderService;
import com.groom.order.presentation.dto.request.OrderCreateRequest;
// import com.groom.order.presentation.dto.request.OrderStatusChangeRequest;
import com.groom.order.presentation.dto.response.OrderCreateResponse;
import com.groom.order.presentation.dto.response.OrderResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Order", description = "주문 생성 및 조회 관련 API") // 1. API 그룹 이름
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v2/orders")
public class OrderController {

	private final OrderService orderService;

	@Operation(summary = "주문 생성", description = "인증된 사용자의 정보로 주문을 생성합니다.") // 2. 메서드 설명
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "주문 생성 성공", content = @Content(schema = @Schema(implementation = UUID.class, example = "7ba12345-1234-1234-1234-123456789abc")))
	})
	@PostMapping
	public ResponseEntity<OrderCreateResponse> createOrder(
			@RequestBody OrderCreateRequest request,
			// @RequestHeader("X-User-Id") UUID userId, // 나중에 게이트웨이에서 헤더로 넘어옴
			@AuthenticationPrincipal CustomUserDetails userDetails) {

		UUID buyerId = userDetails.getUserId();

		UUID orderId = orderService.createOrder(buyerId, request);
		return ResponseEntity.ok(new OrderCreateResponse(orderId));
	}

	@Operation(summary = "내 주문 목록 조회", description = "로그인한 사용자의 주문 내역을 조회합니다.")
	@GetMapping
	public ResponseEntity<Page<OrderResponse>> getMyOrders(
			@AuthenticationPrincipal CustomUserDetails userDetails,
			@PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
		UUID buyerId = userDetails.getUserId();
		return ResponseEntity.ok(orderService.getMyOrders(buyerId, pageable));
	}

	@GetMapping("/{orderId}")
	public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
		OrderResponse response = orderService.getOrder(orderId);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "상품별 주문 목록 조회", description = "특정 상품(ProductId)이 포함된 모든 주문 내역을 조회합니다.")
	@GetMapping("/product/{productId}")
	public ResponseEntity<List<OrderResponse>> getOrdersByProduct(@PathVariable UUID productId) {
		List<OrderResponse> responses = orderService.getOrdersByProduct(productId);
		return ResponseEntity.ok(responses);
	}

	@PostMapping("/{orderId}/cancel")
	public ResponseEntity<String> cancelOrder(@PathVariable UUID orderId) {
		orderService.cancelOrder(orderId);
		return ResponseEntity.ok("주문이 성공적으로 취소되었습니다.");
	}

	// @Operation(summary = "배송 시작 처리", description = "관리자가 주문 상품들을 배송 중 상태로
	// 변경합니다.")
	// @PreAuthorize("hasAnyRole('MANAGER', 'MASTER')") // MANAGER 또는 MASTER 권한이 있어야
	// @PostMapping("/shipping")
	// public ResponseEntity<String> startShipping(@RequestBody
	// OrderStatusChangeRequest request) {
	// orderService.startShipping(request);
	// return ResponseEntity.ok("선택한 상품이 배송 시작 상태로 변경되었습니다.");
	// }
	//
	// @Operation(summary = "배송 완료 처리", description = "관리자가 주문 상품들을 배송 완료 상태로
	// 변경합니다.")
	// @PostMapping("/delivered")
	// @PreAuthorize("hasAnyRole('MANAGER', 'MASTER')") // MANAGER 또는 MASTER 권한이 있어야
	// public ResponseEntity<String> completeDelivery(@RequestBody
	// OrderStatusChangeRequest request) {
	// orderService.completeDelivery(request);
	// return ResponseEntity.ok("선택한 상품이 배송 완료 상태로 변경되었습니다.");
	// }

	// @Operation(summary = "구매 확정", description = "배송이 완료된 주문을 구매 확정 처리합니다.")
	// @PostMapping("/{orderId}/confirm")
	// public ResponseEntity<String> confirmOrder(
	// @PathVariable UUID orderId,
	// @AuthenticationPrincipal CustomUserDetails userDetails
	// ) {
	// // 1. JWT에서 사용자 ID 추출
	// UUID currentUserId = userDetails.getUserId();
	//
	// // 2. 서비스 호출
	// orderService.confirmOrder(orderId, currentUserId);
	//
	// return ResponseEntity.ok("구매가 정상적으로 확정되었습니다.");
	// }
}
