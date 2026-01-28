package com.groom.cart.application.event;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.groom.common.event.envelope.EventEnvelope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartEventConsumer {

    @KafkaListener(topics = "${event.kafka.topics.order:order-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleEvent(EventEnvelope event, org.springframework.kafka.support.Acknowledgment ack) {
        log.info("[CartEvent] Received event: type={}, id={}", event.getEventType(), event.getEventId());

        if ("ORDER_CONFIRMED".equals(event.getEventType().name())) {
            log.info("[CartEvent] Processing ORDER_CONFIRMED event. payload={}", event.getPayload());
            // TODO: Implement cart cleanup logic here
        }
        ack.acknowledge();
    }
}
