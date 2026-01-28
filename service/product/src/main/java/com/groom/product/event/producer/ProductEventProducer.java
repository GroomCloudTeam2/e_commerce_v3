package com.groom.product.event.producer;

import java.util.List;
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
import com.groom.common.event.payload.StockDeductedPayload;
import com.groom.common.event.payload.StockDeductionFailedPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventProducer {

    private final KafkaTemplate<String, EventEnvelope> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${event.kafka.topics.order:order-events}")
    private String topic;

    public void publishStockDeducted(StockDeductedPayload payload) {
        log.info("[ProductEvent] StockDeductedEvent 발행 요청 - orderId={}, items={}", payload.getOrderId(),
                payload.getItems().size());

        try {
            EventEnvelope envelope = EventEnvelope.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(EventType.STOCK_DEDUCTED)
                    .aggregateType("PRODUCT")
                    .aggregateId(payload.getOrderId().toString()) // Using OrderId as aggregateId for now, or maybe we
                                                                  // should use a product ID if available? But it's a
                                                                  // bulk event.
                    .occurredAt(java.time.Instant.now())
                    .producer("service-product")
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();

            publishAfterCommit(payload.getOrderId().toString(), envelope);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload", e);
        }
    }

    public void publishStockDeductionFailed(StockDeductionFailedPayload payload) {
        log.warn("[ProductEvent] StockDeductionFailedEvent 발행 요청 - orderId={}, reason={}", payload.getOrderId(),
                payload.getFailReason());

        try {
            EventEnvelope envelope = EventEnvelope.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(EventType.STOCK_DEDUCTION_FAILED)
                    .aggregateType("PRODUCT")
                    .aggregateId(payload.getOrderId().toString())
                    .occurredAt(java.time.Instant.now())
                    .producer("service-product")
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();

            publishAfterCommit(payload.getOrderId().toString(), envelope);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize payload", e);
        }
    }

    private void publishAfterCommit(String key, EventEnvelope event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("[ProductEvent] 트랜잭션 활성 상태 → afterCommit 발행 예약. eventType={}", event.getEventType());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    log.debug("[ProductEvent] afterCommit 발행 실행. eventType={}", event.getEventType());
                    kafkaTemplate.send(topic, key, event);
                }
            });
            return;
        }

        log.debug("[ProductEvent] 트랜잭션 없음 → 즉시 발행. eventType={}", event.getEventType());
        kafkaTemplate.send(topic, key, event);
    }
}
