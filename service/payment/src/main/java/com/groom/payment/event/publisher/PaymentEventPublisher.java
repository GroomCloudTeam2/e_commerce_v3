package com.groom.payment.event.publisher;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.common.event.payload.PaymentCompletedPayload;
import com.groom.common.event.payload.PaymentFailedPayload;
import com.groom.common.event.payload.RefundFailedPayload;
import com.groom.common.event.payload.RefundSucceededPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

	private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;
	private final ObjectMapper objectMapper;

	@Value("${event.kafka.topics.order:order-events}")
	private String topic;

	public void publishPaymentCompleted(UUID orderId, String paymentKey, Long amount) {
		log.info("[PaymentEvent] PaymentCompletedEvent 발행 요청 - orderId={}, amount={}", orderId, amount);

		PaymentCompletedPayload payload = PaymentCompletedPayload.builder()
				.orderId(orderId)
				.paymentKey(paymentKey)
				.amount(amount)
				.build();

		try {
			EventEnvelope envelope = EventEnvelope.builder()
					.eventId(UUID.randomUUID().toString())
					.eventType(EventType.PAYMENT_COMPLETED)
					.aggregateType("PAYMENT")
					.aggregateId(orderId.toString())
					.occurredAt(java.time.Instant.now())
					.producer("service-payment")
					.payload(objectMapper.writeValueAsString(payload))
					.build();

			publishAfterCommit(orderId.toString(), envelope);
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize payload", e);
		}
	}

	public void publishPaymentFailed(UUID orderId, String paymentKey, Long amount, String failCode,
			String failMessage) {
		log.warn("[PaymentEvent] PaymentFailEvent 발행 요청 - orderId={}, amount={}, failCode={}, message={}",
				orderId, amount, failCode, failMessage);

		PaymentFailedPayload payload = PaymentFailedPayload.builder()
				.orderId(orderId)
				.paymentKey(paymentKey)
				.amount(amount)
				.failCode(failCode)
				.failMessage(failMessage)
				.build();

		try {
			EventEnvelope envelope = EventEnvelope.builder()
					.eventId(UUID.randomUUID().toString())
					.eventType(EventType.PAYMENT_FAILED)
					.aggregateType("PAYMENT")
					.aggregateId(orderId.toString())
					.occurredAt(java.time.Instant.now())
					.producer("service-payment")
					.payload(objectMapper.writeValueAsString(payload))
					.build();

			publishAfterCommit(orderId.toString(), envelope);
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize payload", e);
		}
	}

	public void publishRefundSucceeded(UUID orderId, String paymentKey, Long cancelAmount) {
		log.info("[PaymentEvent] RefundSucceededEvent 발행 요청 - orderId={}, cancelAmount={}", orderId, cancelAmount);

		RefundSucceededPayload payload = RefundSucceededPayload.builder()
				.orderId(orderId)
				.paymentKey(paymentKey)
				.cancelAmount(cancelAmount)
				.build();

		try {
			EventEnvelope envelope = EventEnvelope.builder()
					.eventId(UUID.randomUUID().toString())
					.eventType(EventType.REFUND_SUCCEEDED)
					.aggregateType("PAYMENT")
					.aggregateId(orderId.toString())
					.occurredAt(java.time.Instant.now())
					.producer("service-payment")
					.payload(objectMapper.writeValueAsString(payload))
					.build();

			publishAfterCommit(orderId.toString(), envelope);
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize payload", e);
		}
	}

	public void publishRefundFailed(UUID orderId, String paymentKey, Long cancelAmount, String failCode,
			String failMessage) {
		log.error("[PaymentEvent] RefundFailEvent 발행 요청 - orderId={}, cancelAmount={}, failCode={}, message={}",
				orderId, cancelAmount, failCode, failMessage);

		RefundFailedPayload payload = RefundFailedPayload.builder()
				.orderId(orderId)
				.paymentKey(paymentKey)
				.cancelAmount(cancelAmount)
				.failCode(failCode)
				.failMessage(failMessage)
				.build();

		try {
			EventEnvelope envelope = EventEnvelope.builder()
					.eventId(UUID.randomUUID().toString())
					.eventType(EventType.REFUND_FAILED)
					.aggregateType("PAYMENT")
					.aggregateId(orderId.toString())
					.occurredAt(java.time.Instant.now())
					.producer("service-payment")
					.payload(objectMapper.writeValueAsString(payload))
					.build();

			publishAfterCommit(orderId.toString(), envelope);
		} catch (JsonProcessingException e) {
			log.error("Failed to serialize payload", e);
		}
	}

	private void publishAfterCommit(String key, EventEnvelope event) {
		// 트랜잭션 안이면 커밋 이후 발행
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			log.debug("[PaymentEvent] 트랜잭션 활성 상태 → afterCommit 발행 예약. eventType={}", event.getEventType());

			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					log.debug("[PaymentEvent] afterCommit 발행 실행. eventType={}", event.getEventType());
					kafkaTemplate.send(topic, key, event);
				}
			});
			return;
		}

		// 트랜잭션 밖이면 즉시 발행
		log.debug("[PaymentEvent] 트랜잭션 없음 → 즉시 발행. eventType={}", event.getEventType());
		kafkaTemplate.send(topic, key, event);
	}
}
