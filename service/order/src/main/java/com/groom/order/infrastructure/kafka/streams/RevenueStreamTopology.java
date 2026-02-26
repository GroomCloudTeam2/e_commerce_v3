package com.groom.order.infrastructure.kafka.streams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.common.event.payload.OrderConfirmedItemPayload;
import com.groom.common.event.payload.OrderConfirmedPayload;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RevenueStreamTopology {

    private final ObjectMapper objectMapper;
    private final String topic;

    public RevenueStreamTopology(ObjectMapper objectMapper, 
                                 @Value("${event.kafka.topics.order:order-events}") String topic) {
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Autowired
    public void buildPipeline(StreamsBuilder streamsBuilder) {
        JsonSerde<EventEnvelope> envelopeSerde = new JsonSerde<>(EventEnvelope.class, objectMapper);

        KStream<String, EventEnvelope> messageStream = streamsBuilder.stream(topic, 
                Consumed.with(Serdes.String(), envelopeSerde));

        messageStream
            .filter((key, envelope) -> envelope.getEventType() == EventType.ORDER_CONFIRMED)
            .flatMap((key, envelope) -> extractStoreRevenue(envelope))
            .groupByKey(org.apache.kafka.streams.kstream.Grouped.with(Serdes.String(), Serdes.Long()))
            .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofHours(1), Duration.ofMinutes(10)))
            .aggregate(
                () -> 0L,
                (key, amount, aggregate) -> aggregate + amount,
                Materialized.<String, Long, WindowStore<Bytes, byte[]>>as("store-revenue-store")
                    .withKeySerde(Serdes.String())
                    .withValueSerde(Serdes.Long())
            );
    }

    private Iterable<KeyValue<String, Long>> extractStoreRevenue(EventEnvelope envelope) {
        List<KeyValue<String, Long>> results = new ArrayList<>();
        try {
            OrderConfirmedPayload payload = objectMapper.readValue(envelope.getPayload(), OrderConfirmedPayload.class);
            
            if (payload.getItems() != null) {
                for (OrderConfirmedItemPayload item : payload.getItems()) {
                    // Key: Owner ID (Store ID), Value: Subtotal
                    results.add(new KeyValue<>(item.getOwnerId().toString(), item.getSubtotal()));
                }
            } else if (payload.getTotalAmount() != null) {
                // Fallback if no items: attribute to "TOTAL" or handle error
                // For now, let's log and skip specific aggregation if items are missing but we expect them
                log.warn("OrderConfirmed event missing items list. orderId={}", payload.getOrderId());
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize OrderConfirmedPayload", e);
        }
        return results;
    }
}
