package com.groom.product.product.application.event.listener;

import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.groom.product.product.application.dto.StockManagement;
import com.groom.common.event.payload.OrderCancelledPayload;
import com.groom.common.event.payload.PaymentCompletedPayload;
import com.groom.common.event.payload.PaymentFailedPayload;
import com.groom.common.event.payload.StockDeductedPayload;
import com.groom.common.event.payload.StockDeductionFailedPayload;
import com.groom.product.event.producer.ProductEventProducer;
import com.groom.product.product.application.service.ProductServiceV1;
import com.groom.product.product.infrastructure.cache.StockRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Product 도메인 이벤트 리스너
 * Payment, Order 도메인에서 발행한 이벤트를 수신하여 재고 처리를 수행합니다.
 *
 * 상품 정보(items)는 가점유 시점에 Redis에 저장된 매핑을 사용합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventListener {

	private final ProductServiceV1 productServiceV1;
	private final ProductEventProducer productEventProducer;
	private final StockRedisService stockRedisService;

	/**
	 * 결제 완료 이벤트 처리
	 * - 가점유된 재고를 DB에서 확정 차감
	 * - 성공 시 StockDeductedEvent 발행
	 * - 실패 시 StockDeductionFailedEvent 발행
	 */
	@Async("eventExecutor")
	@EventListener
	@Transactional
	public void handlePaymentCompleted(PaymentCompletedPayload event) {
		log.info("[Product] PaymentCompletedEvent 수신 - orderId: {}", event.getOrderId());

		// Redis에서 주문-상품 매핑 조회
		List<StockManagement> stockManagements = stockRedisService.getOrderStockItems(event.getOrderId());

		if (stockManagements.isEmpty()) {
			log.error("[Product] 주문-상품 매핑을 찾을 수 없음 - orderId: {}", event.getOrderId());
			productEventProducer.publishStockDeductionFailed(
					StockDeductionFailedPayload.builder()
							.orderId(event.getOrderId())
							.failReason("주문-상품 매핑을 찾을 수 없습니다.")
							.failedItems(List.of())
							.build());
			return;
		}

		try {
			// DB 재고 확정 차감
			productServiceV1.confirmStockBulk(stockManagements);

			// 성공 이벤트 발행
			List<StockDeductedPayload.DeductedItem> deductedItems = stockManagements.stream()
					.map(item -> StockDeductedPayload.DeductedItem.builder()
							.productId(item.getProductId())
							.quantity(item.getQuantity())
							.build())
					.toList();

			productEventProducer.publishStockDeducted(
					StockDeductedPayload.builder()
							.orderId(event.getOrderId())
							.items(deductedItems)
							.build());
			System.out.println("DEBUG: Called publishStockDeducted");

			log.info("[Product] 재고 확정 차감 완료 - orderId: {}", event.getOrderId());

		} catch (Exception e) {
			log.error("[Product] 재고 확정 차감 실패 - orderId: {}, error: {}", event.getOrderId(), e.getMessage());

			// 실패 이벤트 발행
			List<StockDeductionFailedPayload.FailedItem> failedItems = stockManagements.stream()
					.map(item -> StockDeductionFailedPayload.FailedItem.builder()
							.productId(item.getProductId())
							.requestedQuantity(item.getQuantity())
							.reason(e.getMessage())
							.build())
					.toList();

			productEventProducer.publishStockDeductionFailed(
					StockDeductionFailedPayload.builder()
							.orderId(event.getOrderId())
							.failReason(e.getMessage())
							.failedItems(failedItems)
							.build());
		}
	}

	/**
	 * 결제 실패 이벤트 처리
	 * - 가점유된 재고를 Redis에서 복구
	 */
	@Async("eventExecutor")
	@EventListener
	@Transactional
	public void handlePaymentFail(PaymentFailedPayload event) {
		log.info("[Product] PaymentFailEvent 수신 - orderId: {}, failMessage: {}",
				event.getOrderId(), event.getFailMessage());

		// Redis에서 주문-상품 매핑 조회
		List<StockManagement> stockManagements = stockRedisService.getOrderStockItems(event.getOrderId());

		if (stockManagements.isEmpty()) {
			log.warn("[Product] 주문-상품 매핑을 찾을 수 없음 - orderId: {}", event.getOrderId());
			return;
		}

		try {
			// Redis 가점유 재고 복구
			productServiceV1.releaseStockBulk(stockManagements);

			// 매핑 삭제
			stockRedisService.deleteOrderStockItems(event.getOrderId());

			log.info("[Product] 가점유 재고 복구 완료 - orderId: {}", event.getOrderId());

		} catch (Exception e) {
			log.error("[Product] 가점유 재고 복구 실패 - orderId: {}, error: {}", event.getOrderId(), e.getMessage());
		}
	}

	/**
	 * 주문 취소 이벤트 처리
	 * - Redis 가용 재고 복구 + DB 실재고 복구
	 */
	@Async("eventExecutor")
	@EventListener
	@Transactional
	public void handleOrderCancelled(OrderCancelledPayload event) {
		log.info("[Product] OrderCancelledEvent 수신 - orderId: {}, reason: {}",
				event.getOrderId(), event.getReason());

		// Redis에서 주문-상품 매핑 조회
		List<StockManagement> stockManagements = stockRedisService.getOrderStockItems(event.getOrderId());

		if (stockManagements.isEmpty()) {
			log.warn("[Product] 주문-상품 매핑을 찾을 수 없음 (이미 처리됨?) - orderId: {}", event.getOrderId());
			return;
		}

		try {
			// Redis + DB 재고 복구
			productServiceV1.restoreStockBulk(stockManagements);

			// 매핑 삭제
			stockRedisService.deleteOrderStockItems(event.getOrderId());

			log.info("[Product] 재고 복구 완료 - orderId: {}", event.getOrderId());

		} catch (Exception e) {
			log.error("[Product] 재고 복구 실패 - orderId: {}, error: {}", event.getOrderId(), e.getMessage());
		}
	}
}
