package com.groom.order.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
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
import com.groom.common.event.payload.PaymentCompletedPayload;
import com.groom.common.event.payload.StockDeductedPayload;
import com.groom.order.domain.entity.Order;
import com.groom.order.domain.status.OrderStatus;
import com.groom.order.domain.repository.OrderRepository;
import com.groom.order.infrastructure.client.ProductClient;
import com.groom.order.infrastructure.client.UserClient;

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
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
        DefaultKafkaProducerFactory<String, Object> pf = new DefaultKafkaProducerFactory<>(producerProps);
        testKafkaTemplate = new KafkaTemplate<>(pf);

        // Consumer Setup (to verify Order service output)
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class); // Read JSON as String
        DefaultKafkaConsumerFactory<String, Object> cf = new DefaultKafkaConsumerFactory<>(consumerProps);
        testConsumer = cf.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(testConsumer, topic);
    }

    @Test
    @DisplayName("Order Flow: Created -> Payment Completed -> Stock Deducted -> Confirmed")
    void testOrderFlow() throws Exception {
        // 1. Create Order
        Order order = Order.builder()
                .buyerId(UUID.randomUUID())
                .orderNumber(UUID.randomUUID().toString().substring(0, 20))
                .totalPaymentAmount(1000L)
                .recipientName("Test User")
                .recipientPhone("010-1234-5678")
                .zipCode("12345")
                .shippingAddress("Test Address")
                .shippingMemo("Test Memo")
                .build();
        
        // Add an item to the order
        com.groom.order.domain.entity.OrderItem item = com.groom.order.domain.entity.OrderItem.builder()
                .order(order)
                .productId(UUID.randomUUID())
                .ownerId(UUID.randomUUID())
                .productTitle("Test Product")
                .quantity(1)
                .unitPrice(1000L)
                .build();
        order.getItems().add(item);

        orderRepository.save(order);
        UUID orderId = order.getOrderId(); // Use UUID orderId

        // 2. Simulate Payment Completed Event
        PaymentCompletedPayload paymentPayload = PaymentCompletedPayload.builder()
                .orderId(orderId)
                .paymentKey("test-payment-key")
                .amount(1000L)
                .build();

        EventEnvelope paymentEvent = EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.PAYMENT_COMPLETED)
                .aggregateType("PAYMENT")
                .aggregateId(orderId.toString())
                .occurredAt(Instant.now())
                .producer("service-payment")
                .payload(objectMapper.writeValueAsString(paymentPayload))
                .build();

        testKafkaTemplate.send(topic, orderId.toString(), paymentEvent);

        // 3. Verify Order Status -> PAID
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        });

        // 4. Simulate Stock Deducted Event
        StockDeductedPayload stockPayload = StockDeductedPayload.builder()
                .orderId(orderId)
                .items(java.util.List.of())
                .build();

        EventEnvelope stockEvent = EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.STOCK_DEDUCTED)
                .aggregateType("PRODUCT")
                .aggregateId(orderId.toString())
                .occurredAt(Instant.now())
                .producer("service-product")
                .payload(objectMapper.writeValueAsString(stockPayload))
                .build();

        testKafkaTemplate.send(topic, orderId.toString(), stockEvent);

        // 5. Verify Order Status -> CONFIRMED
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Order updatedOrder = orderRepository.findById(orderId).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        });
    }
}
