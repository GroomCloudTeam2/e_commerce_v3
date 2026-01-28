package com.groom.product.event.consumer;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.common.event.payload.OrderCancelledPayload;
import com.groom.common.event.payload.PaymentCompletedPayload;
import com.groom.common.event.payload.PaymentFailedPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${event.kafka.topics.order:order-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleEvent(EventEnvelope event, org.springframework.kafka.support.Acknowledgment ack) {
        log.debug("[ProductEventConsumer] Received event: type={}, id={}", event.getEventType(), event.getEventId());

        try {
            if (event.getEventType() == EventType.PAYMENT_COMPLETED) {
                PaymentCompletedPayload payload = objectMapper.readValue(event.getPayload(),
                        PaymentCompletedPayload.class);
                eventPublisher.publishEvent(payload);
                log.info("[ProductEventConsumer] Published PaymentCompletedPayload locally. orderId={}",
                        payload.getOrderId());
            } else if (event.getEventType() == EventType.PAYMENT_FAILED) {
                PaymentFailedPayload payload = objectMapper.readValue(event.getPayload(), PaymentFailedPayload.class);
                eventPublisher.publishEvent(payload);
                log.info("[ProductEventConsumer] Published PaymentFailedPayload locally. orderId={}",
                        payload.getOrderId());
            } else if (event.getEventType() == EventType.ORDER_CANCELLED) {
                OrderCancelledPayload payload = objectMapper.readValue(event.getPayload(), OrderCancelledPayload.class);
                eventPublisher.publishEvent(payload);
                log.info("[ProductEventConsumer] Published OrderCancelledPayload locally. orderId={}",
                        payload.getOrderId());
            }
            ack.acknowledge();
        } catch (JsonProcessingException e) {
            log.error("[ProductEventConsumer] Failed to deserialize payload for event type: {}", event.getEventType(),
                    e);
        }
    }
}
