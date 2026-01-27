package com.groom.order.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.order.application.service.OrderService;
import com.groom.order.domain.repository.OrderRepository;
import com.groom.order.infrastructure.client.ProductClient;
import com.groom.order.infrastructure.client.UserClient;
import com.groom.order.infrastructure.client.dto.UserAddressResponse;
import com.groom.order.presentation.dto.request.OrderCreateItemRequest;
import com.groom.order.presentation.dto.request.OrderCreateRequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "${event.kafka.topic:domain-events}" })
@ActiveProfiles("test")
@Testcontainers
class OrderKafkaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderOutboxPublisher orderOutboxPublisher;

    @MockBean
    private UserClient userClient;

    @MockBean
    private ProductClient productClient;

    @Value("${event.kafka.topic:domain-events}")
    private String topic;

    private KafkaTemplate<String, Object> testKafkaTemplate;
    private Consumer<String, Object> testConsumer;

    @BeforeEach
    void setUp() {
        // Producer Setup
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);
        DefaultKafkaProducerFactory<String, Object> pf = new DefaultKafkaProducerFactory<>(producerProps);
        testKafkaTemplate = new KafkaTemplate<>(pf);

        // Consumer Setup (to verify Order service output)
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class); // Read JSON as
                                                                                                     // String
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        DefaultKafkaConsumerFactory<String, Object> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        testConsumer = cf.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(testConsumer, topic);
    }

    @Test
    @DisplayName("Order Created -> Event Published to Kafka")
    void testOrderCreatedEvent() throws Exception {
        // 1. Mock External Clients
        UUID userId = UUID.randomUUID();

        when(userClient.getUserAddress(any(), any()))
                .thenReturn(new UserAddressResponse("Test Recipient", "010-1234-5678", "12345", "Test Address", "101"));

        // 2. Create Order
        OrderCreateRequest request = new OrderCreateRequest();
        request.setTotalAmount(1000L);

        OrderCreateItemRequest itemRequest = new OrderCreateItemRequest();
        itemRequest.setProductId(UUID.randomUUID());
        itemRequest.setVariantId(UUID.randomUUID());
        itemRequest.setProductTitle("Test Product");
        itemRequest.setQuantity(1);
        itemRequest.setUnitPrice(1000L);
        request.setItems(java.util.List.of(itemRequest));

        UUID orderId = orderService.createOrder(userId, request);

        // 3. Trigger Outbox Publisher manually
        orderOutboxPublisher.publish();

        // 4. Verify Event Published to Kafka
        ConsumerRecord<String, Object> record = KafkaTestUtils.getSingleRecord(testConsumer, topic,
                Duration.ofSeconds(10));
        assertThat(record).isNotNull();
        String payloadJson = (String) record.value();
        EventEnvelope event = objectMapper.readValue(payloadJson, EventEnvelope.class);

        assertThat(event.getEventType()).isEqualTo(EventType.ORDER_CREATED);
        assertThat(event.getAggregateId()).isEqualTo(orderId.toString());
    }
}
