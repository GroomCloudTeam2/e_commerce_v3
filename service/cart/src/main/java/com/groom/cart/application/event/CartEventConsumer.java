package com.groom.cart.application.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.cart.application.CartService;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.common.event.payload.OrderConfirmedPayload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartEventConsumer {

    private final ObjectMapper objectMapper;
    private final CartService cartService;

    @KafkaListener(topics = "${event.kafka.topics.order:order-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleEvent(EventEnvelope event, org.springframework.kafka.support.Acknowledgment ack) {
        log.info("[CartEvent] Received event: type={}, id={}", event.getEventType(), event.getEventId());

        try {
            if (EventType.ORDER_CONFIRMED == event.getEventType()) {
                OrderConfirmedPayload payload = objectMapper.readValue(event.getPayload(), OrderConfirmedPayload.class);
                log.info("[CartEvent] Processing ORDER_CONFIRMED event. orderId={}, userId={}",
                        payload.getOrderId(), payload.getUserId());
                cartService.clearCart(payload.getUserId());
            }
        } catch (Exception e) {
            log.error("[CartEvent] Failed to process event: type={}, id={}, error={}",
                    event.getEventType(), event.getEventId(), e.getMessage(), e);
        }

        ack.acknowledge();
    }
}
