package com.groom.order.application.event;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.common.event.payload.OrderConfirmedPayload;
import com.groom.common.event.payload.PaymentCompletedPayload;
import com.groom.common.event.payload.PaymentFailedPayload;
import com.groom.common.event.payload.RefundFailedPayload;
import com.groom.common.event.payload.RefundSucceededPayload;
import com.groom.common.event.payload.StockDeductedPayload;
import com.groom.common.event.payload.StockDeductionFailedPayload;
import com.groom.order.domain.entity.Order;
import com.groom.order.domain.repository.OrderRepository;
import com.groom.order.infrastructure.kafka.OrderOutboxService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaConsumer {

	private final OrderRepository orderRepository;
	private final OrderOutboxService outboxService;
	private final ObjectMapper objectMapper;

	@Value("${spring.application.name}")
	private String producer;

	@KafkaListener(topics = "${event.kafka.topics.order:order-events}", groupId = "${spring.kafka.consumer.group-id}")
	@Transactional
	public void handle(EventEnvelope envelope, org.springframework.kafka.support.Acknowledgment ack) {
		if (producer.equals(envelope.getProducer())) {
			ack.acknowledge();
			return; // skip self-produced events
		}

		EventType eventType = envelope.getEventType();
		switch (eventType) {
			case PAYMENT_COMPLETED -> handlePaymentCompleted(envelope);
			case STOCK_DEDUCTED -> handleStockDeducted(envelope);
			case PAYMENT_FAILED -> handlePaymentFailed(envelope);
			case STOCK_DEDUCTION_FAILED -> handleStockDeductionFailed(envelope);
			case REFUND_SUCCEEDED -> handleRefundSucceeded(envelope);
			case REFUND_FAILED -> handleRefundFailed(envelope);
			default -> {
			}
		}
		ack.acknowledge();
	}

	private void handlePaymentCompleted(EventEnvelope envelope) {
		PaymentCompletedPayload payload = readPayload(envelope, PaymentCompletedPayload.class);
		log.info("[Order] PaymentCompletedEvent 수신 - orderId: {}", payload.getOrderId());
		Order order = orderRepository.findById(payload.getOrderId())
				.orElseThrow(() -> new IllegalStateException("Order not found: " + payload.getOrderId()));
		order.confirmPayment();
		orderRepository.save(order);
	}

	private void handleStockDeducted(EventEnvelope envelope) {
		StockDeductedPayload payload = readPayload(envelope, StockDeductedPayload.class);
		log.info("[Order] StockDeductedEvent 수신 - orderId: {}", payload.getOrderId());
		Order order = orderRepository.findById(payload.getOrderId())
				.orElseThrow(() -> new IllegalStateException("Order not found: " + payload.getOrderId()));
		order.complete();
		orderRepository.save(order);

		outboxService.save(
				EventType.ORDER_CONFIRMED,
				"ORDER",
				payload.getOrderId(),
				payload.getOrderId().toString(),
				OrderConfirmedPayload.builder()
						.orderId(payload.getOrderId())
						.userId(order.getBuyerId())
						.confirmedAt(Instant.now())
						.build());
	}

	private void handlePaymentFailed(EventEnvelope envelope) {
		PaymentFailedPayload payload = readPayload(envelope, PaymentFailedPayload.class);
		log.info("[Order] PaymentFailedEvent 수신 - orderId: {}", payload.getOrderId());
		Order order = orderRepository.findById(payload.getOrderId())
				.orElseThrow(() -> new IllegalStateException("Order not found: " + payload.getOrderId()));
		order.fail();
		orderRepository.save(order);
	}

	private void handleStockDeductionFailed(EventEnvelope envelope) {
		StockDeductionFailedPayload payload = readPayload(envelope, StockDeductionFailedPayload.class);
		log.info("[Order] StockDeductionFailedEvent 수신 - orderId: {}", payload.getOrderId());
		Order order = orderRepository.findById(payload.getOrderId())
				.orElseThrow(() -> new IllegalStateException("Order not found: " + payload.getOrderId()));
		order.fail();
		orderRepository.save(order);
	}

	private void handleRefundSucceeded(EventEnvelope envelope) {
		RefundSucceededPayload payload = readPayload(envelope, RefundSucceededPayload.class);
		log.info("[Order] RefundSucceededEvent 수신 - orderId: {}", payload.getOrderId());
		Order order = orderRepository.findById(payload.getOrderId())
				.orElseThrow(() -> new IllegalStateException("Order not found: " + payload.getOrderId()));
		order.cancel();
		orderRepository.save(order);
	}

	private void handleRefundFailed(EventEnvelope envelope) {
		RefundFailedPayload payload = readPayload(envelope, RefundFailedPayload.class);
		log.error("[Order] RefundFailedEvent 수신 - orderId: {}", payload.getOrderId());
		Order order = orderRepository.findById(payload.getOrderId())
				.orElseThrow(() -> new IllegalStateException("Order not found: " + payload.getOrderId()));
		order.requireManualCheck();
		orderRepository.save(order);
	}

	private <T> T readPayload(EventEnvelope envelope, Class<T> type) {
		try {
			return objectMapper.readValue(envelope.getPayload(), type);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to read payload for " + envelope.getEventType(), e);
		}
	}
}
