package com.groom.order.infrastructure.kafka.streams;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.kafka.support.serializer.JsonSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.common.event.payload.OrderConfirmedItemPayload;
import com.groom.common.event.payload.OrderConfirmedPayload;

class RevenueStreamTopologyTest {

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, EventEnvelope> inputTopic;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Setup Topology
        StreamsBuilder builder = new StreamsBuilder();
        RevenueStreamTopology topologyProvider = new RevenueStreamTopology(objectMapper, "order-events");
        topologyProvider.buildPipeline(builder);
        Topology topology = builder.build();

        // Setup TestDriver
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        testDriver = new TopologyTestDriver(topology, props);

        // Define Serdes
        JsonSerializer<EventEnvelope> keySerializer = new JsonSerializer<>(objectMapper); // Actually unused for key
        JsonSerializer<EventEnvelope> valueSerializer = new JsonSerializer<>(objectMapper);
        
        // Error here: Key is String.
        inputTopic = testDriver.createInputTopic("order-events", new StringSerializer(), valueSerializer);
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }

    @Test
    void shouldAggregateRevenueByStore() throws Exception {
        // Given
        UUID storeId1 = UUID.randomUUID();
        UUID storeId2 = UUID.randomUUID();

        OrderConfirmedItemPayload item1 = OrderConfirmedItemPayload.builder()
                .ownerId(storeId1)
                .subtotal(1000L)
                .quantity(1)
                .build();
        
        OrderConfirmedItemPayload item2 = OrderConfirmedItemPayload.builder()
                .ownerId(storeId2)
                .subtotal(2000L)
                .quantity(1)
                .build();

        OrderConfirmedItemPayload item3 = OrderConfirmedItemPayload.builder()
                .ownerId(storeId1)
                .subtotal(500L)
                .quantity(1)
                .build();

        OrderConfirmedPayload payload = OrderConfirmedPayload.builder()
                .orderId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .confirmedAt(Instant.now())
                .totalAmount(3500L)
                .items(List.of(item1, item2, item3))
                .build();

        EventEnvelope envelope = EventEnvelope.builder()
                .eventType(EventType.ORDER_CONFIRMED)
                .eventId(UUID.randomUUID().toString())
                .aggregateId(payload.getOrderId().toString())
                .payload(objectMapper.writeValueAsString(payload))
                .build();

        // When
        inputTopic.pipeInput("order-1", envelope);

        // Then
        org.apache.kafka.streams.state.WindowStore<String, Long> store = testDriver.getWindowStore("store-revenue-store");
        
        // Allow some time for processing? (TopologyTestDriver is synchronous usually)
        
        // Assertions for Store 1 (1000 + 500 = 1500)
        var iterator = store.fetch(storeId1.toString(), Instant.now().minus(Duration.ofHours(2)), Instant.now().plus(Duration.ofHours(2)));
        assertThat(iterator.hasNext()).isTrue();
        KeyValue<Long, Long> entry1 = iterator.next();
        assertThat(entry1.value).isEqualTo(1500L);

        // Assertions for Store 2 (2000)
        var iterator2 = store.fetch(storeId2.toString(), Instant.now().minus(Duration.ofHours(2)), Instant.now().plus(Duration.ofHours(2)));
        assertThat(iterator2.hasNext()).isTrue();
        KeyValue<Long, Long> entry2 = iterator2.next();
        assertThat(entry2.value).isEqualTo(2000L);
    }
}
