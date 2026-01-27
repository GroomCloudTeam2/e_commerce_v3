package com.groom.payment.event.listener;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.groom.common.event.payload.OrderCancelledPayload;
import com.groom.common.event.payload.OrderCreatedPayload;
import com.groom.common.event.payload.StockDeductionFailedPayload;
import com.groom.payment.application.service.PaymentCommandService;
import com.groom.payment.presentation.dto.request.ReqCancelPayment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

	private final PaymentCommandService paymentCommandService;

	/**
	 * 주문 생성 이벤트 수신
	 * Order → Payment
	 */
	@EventListener
	public void handleOrderCreated(OrderCreatedPayload event) {
		log.info("[Payment] OrderCreatedEvent 수신 - orderId: {}, amount: {}",
				event.getOrderId(), event.getTotalAmount());

		// 결제 READY 생성 (멱등)
		paymentCommandService.createReady(event.getOrderId(), event.getTotalAmount());
	}

	/**
	 * 주문 취소 이벤트 수신
	 * Order → Payment
	 */
	@EventListener
	public void handleOrderCancelled(OrderCancelledPayload event) {
		log.info("[Payment] OrderCancelledEvent 수신 - orderId: {}, reason: {}",
				event.getOrderId(), event.getReason());

		// 결제 취소 / 환불 트리거
		paymentCommandService.cancel(
				new ReqCancelPayment(event.getOrderId(), event.getReason()));
	}

	/**
	 * 재고 차감 실패 이벤트 수신
	 * Product → Payment (보상 트랜잭션)
	 */
	@EventListener
	public void handleStockDeductionFailed(StockDeductionFailedPayload event) {
		log.warn("[Payment] StockDeductionFailedEvent 수신 - orderId: {}, reason: {}",
				event.getOrderId(), event.getFailReason());

		// 결제 취소(보상)
		paymentCommandService.cancel(
				new ReqCancelPayment(
						event.getOrderId(),
						event.getFailReason()));
	}
}
