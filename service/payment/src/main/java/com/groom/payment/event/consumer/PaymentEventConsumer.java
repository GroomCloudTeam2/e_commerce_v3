package com.groom.payment.event.consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.common.event.payload.OrderCancelledPayload;
import com.groom.common.event.payload.OrderCreatedPayload;
import com.groom.common.event.payload.StockDeductionFailedPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${event.kafka.topics.order:order-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleEvent(EventEnvelope event, org.springframework.kafka.support.Acknowledgment ack) {
        log.debug("[ProductEventConsumer] Received event: type={}, id={}", event.getEventType(), event.getEventId());

        try {
            if (event.getEventType() == EventType.ORDER_CREATED) {
                OrderCreatedPayload payload = objectMapper.readValue(event.getPayload(), OrderCreatedPayload.class);
                eventPublisher.publishEvent(payload);
                log.info("[PaymentEventConsumer] Published OrderCreatedPayload locally. orderId={}",
                        payload.getOrderId());
            } else if (event.getEventType() == EventType.ORDER_CANCELLED) {
                OrderCancelledPayload payload = objectMapper.readValue(event.getPayload(), OrderCancelledPayload.class);
                eventPublisher.publishEvent(payload);
                log.info("[PaymentEventConsumer] Published OrderCancelledPayload locally. orderId={}",
                        payload.getOrderId());
            } else if (event.getEventType() == EventType.STOCK_DEDUCTION_FAILED) {
                StockDeductionFailedPayload payload = objectMapper.readValue(event.getPayload(),
                        StockDeductionFailedPayload.class);
                eventPublisher.publishEvent(payload);
                log.info("[PaymentEventConsumer] Published StockDeductionFailedPayload locally. orderId={}",
                        payload.getOrderId());
            } else {
                log.info("[PaymentEventConsumer] Ignored event type: {}", event.getEventType());
            }
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("[PaymentEventConsumer] Failed to deserialize payload for event type: {}", event.getEventType(),
                    e);
        }
    }
}
