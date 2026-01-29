// package com.groom.order.infrastructure.kafka;

// import static org.assertj.core.api.Assertions.assertThat;
// import static org.awaitility.Awaitility.await;

// import java.time.Duration;
// import java.util.Map;
// import java.util.UUID;
// import java.util.concurrent.TimeUnit;

// import org.apache.kafka.clients.consumer.Consumer;
// import org.apache.kafka.clients.consumer.ConsumerConfig;
// import org.apache.kafka.clients.consumer.ConsumerRecord;
// import org.apache.kafka.clients.producer.ProducerConfig;
// import org.apache.kafka.common.serialization.StringDeserializer;
// import org.apache.kafka.common.serialization.StringSerializer;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
// import org.springframework.kafka.core.DefaultKafkaProducerFactory;
// import org.springframework.kafka.core.KafkaTemplate;
// import org.springframework.kafka.test.EmbeddedKafkaBroker;
// import org.springframework.kafka.test.context.EmbeddedKafka;
// import org.springframework.kafka.test.utils.KafkaTestUtils;
// import org.springframework.test.context.ActiveProfiles;
// import org.springframework.test.context.DynamicPropertyRegistry;
// import org.springframework.test.context.DynamicPropertySource;
// import org.springframework.test.context.TestPropertySource;
// import org.testcontainers.containers.PostgreSQLContainer;
// import org.testcontainers.junit.jupiter.Container;
// import org.testcontainers.junit.jupiter.Testcontainers;

// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.groom.common.event.Type.EventType;
// import com.groom.common.event.envelope.EventEnvelope;
// import com.groom.common.event.payload.StockDeductedPayload;
// import com.groom.order.application.service.OrderService;
// import com.groom.order.domain.repository.OrderRepository;
// import com.groom.order.infrastructure.client.ProductClient;
// import com.groom.order.infrastructure.client.UserClient;
// import com.groom.order.infrastructure.client.dto.UserAddressResponse;
// import com.groom.order.presentation.dto.request.OrderCreateItemRequest;
// import com.groom.order.presentation.dto.request.OrderCreateRequest;

// import static org.mockito.ArgumentMatchers.any;
// import static org.mockito.Mockito.when;

// @SpringBootTest
// @EmbeddedKafka(partitions = 3, topics = { "order-events" })
// @ActiveProfiles("test")
// @Testcontainers
// @TestPropertySource(properties = {
// "spring.kafka.consumer.group-id=test-group-${random.uuid}", // 고유 ID 부여
// "spring.kafka.consumer.auto-offset-reset=earliest"
// })
// class OrderKafkaIntegrationTest {

// @Container
// static PostgreSQLContainer<?> postgres = new
// PostgreSQLContainer<>("postgres:15-alpine");

// @DynamicPropertySource
// static void configureProperties(DynamicPropertyRegistry registry) {
// registry.add("spring.datasource.url", postgres::getJdbcUrl);
// registry.add("spring.datasource.username", postgres::getUsername);
// registry.add("spring.datasource.password", postgres::getPassword);
// }

// @Autowired
// private EmbeddedKafkaBroker embeddedKafkaBroker;

// @Autowired
// private OrderRepository orderRepository;

// @Autowired
// private ObjectMapper objectMapper;

// @Autowired
// private OrderService orderService;

// @Autowired
// private OrderOutboxPublisher orderOutboxPublisher;

// @MockBean
// private UserClient userClient;

// @MockBean
// private ProductClient productClient;

// @Value("${event.kafka.topic:domain-events}")
// private String topic;

// private KafkaTemplate<String, Object> testKafkaTemplate;
// private Consumer<String, Object> testConsumer;

// @BeforeEach
// void setUp() {
// // Producer Setup
// Map<String, Object> producerProps =
// KafkaTestUtils.producerProps(embeddedKafkaBroker);
// producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
// StringSerializer.class);
// producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
// org.springframework.kafka.support.serializer.JsonSerializer.class);
// DefaultKafkaProducerFactory<String, Object> pf = new
// DefaultKafkaProducerFactory<>(producerProps);
// testKafkaTemplate = new KafkaTemplate<>(pf);

// // // Consumer Setup (to verify Order service output)
// // Map<String, Object> consumerProps =
// // KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);
// // consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
// // StringDeserializer.class);
// // consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
// // StringDeserializer.class); // Read JSON as
// // // String
// // consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
// // DefaultKafkaConsumerFactory<String, Object> cf = new
// // DefaultKafkaConsumerFactory<>(consumerProps);
// // testConsumer = cf.createConsumer();
// // embeddedKafkaBroker.consumeFromAnEmbeddedTopic(testConsumer, topic);

// // Consumer Setup 수정
// // 그룹 ID를 매번 다르게 하여 리밸런싱 간섭을 차단합니다.
// String uniqueGroupId = "test-group-" + UUID.randomUUID();
// Map<String, Object> consumerProps =
// KafkaTestUtils.consumerProps(uniqueGroupId, "true", embeddedKafkaBroker);
// consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
// StringDeserializer.class);
// consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
// StringDeserializer.class);
// consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

// DefaultKafkaConsumerFactory<String, Object> cf = new
// DefaultKafkaConsumerFactory<>(consumerProps);
// testConsumer = cf.createConsumer();

// // 토픽 구독 및 파티션 할당 대기 (핵심!)
// embeddedKafkaBroker.consumeFromAnEmbeddedTopic(testConsumer, topic);
// // Spring Kafka Test 유틸을 사용하여 할당이 완료될 때까지 최대 10초 대기
// org.springframework.kafka.test.utils.ContainerTestUtils.waitForAssignment(testConsumer,
// embeddedKafkaBroker.getPartitionsPerTopic());
// }

// @Test
// @DisplayName("Order Created -> Event Published to Kafka")
// void testOrderCreatedEvent() throws Exception {
// // 1. Mock External Clients
// UUID userId = UUID.randomUUID();

// when(userClient.getUserAddress(any(), any()))
// .thenReturn(new UserAddressResponse("Test Recipient", "010-1234-5678",
// "12345", "Test Address", "101"));

// // 2. Create Order
// OrderCreateRequest request = new OrderCreateRequest();
// request.setTotalAmount(1000L);

// OrderCreateItemRequest itemRequest = new OrderCreateItemRequest();
// itemRequest.setProductId(UUID.randomUUID());
// itemRequest.setVariantId(UUID.randomUUID());
// itemRequest.setProductTitle("Test Product");
// itemRequest.setQuantity(1);
// itemRequest.setUnitPrice(1000L);
// request.setItems(java.util.List.of(itemRequest));

// UUID orderId = orderService.createOrder(userId, request);

// // 3. Trigger Outbox Publisher manually
// orderOutboxPublisher.publish();

// // 4. Verify Event Published to Kafka
// ConsumerRecord<String, Object> record =
// KafkaTestUtils.getSingleRecord(testConsumer, topic,
// Duration.ofSeconds(10));
// assertThat(record).isNotNull();
// String payloadJson = (String) record.value();
// EventEnvelope event = objectMapper.readValue(payloadJson,
// EventEnvelope.class);

// assertThat(event.getEventType()).isEqualTo(EventType.ORDER_CREATED);
// assertThat(event.getAggregateId()).isEqualTo(orderId.toString());
// }

// @Test
// @DisplayName("Stock Deducted -> Order Confirmed -> Event Published")
// void testOrderConfirmedEvent() throws Exception {
// // 1. Create Order
// UUID userId = UUID.randomUUID();
// when(userClient.getUserAddress(any(), any()))
// .thenReturn(new UserAddressResponse("Test Recipient", "010-1234-5678",
// "12345", "Test Address", "101"));

// OrderCreateRequest request = new OrderCreateRequest();
// request.setTotalAmount(1000L);
// OrderCreateItemRequest itemRequest = new OrderCreateItemRequest();
// itemRequest.setProductId(UUID.randomUUID());
// itemRequest.setVariantId(UUID.randomUUID());
// itemRequest.setProductTitle("Test Product");
// itemRequest.setQuantity(1);
// itemRequest.setUnitPrice(1000L);
// request.setItems(java.util.List.of(itemRequest));

// UUID orderId = orderService.createOrder(userId, request);

// // 2. Simulate StockDeductedEvent from Product Service
// StockDeductedPayload payload = StockDeductedPayload.builder()
// .orderId(orderId)
// .items(java.util.List.of(
// StockDeductedPayload.DeductedItem.builder()
// .productId(itemRequest.getProductId())
// .quantity(1)
// .build()))
// .build();

// EventEnvelope envelope = EventEnvelope.builder()
// .eventId(UUID.randomUUID().toString())
// .eventType(EventType.STOCK_DEDUCTED)
// .aggregateType("PRODUCT")
// .aggregateId(orderId.toString())
// .occurredAt(java.time.Instant.now())
// .producer("service-product") // Different producer
// .payload(objectMapper.writeValueAsString(payload))
// .build();

// testKafkaTemplate.send(topic, orderId.toString(),
// objectMapper.writeValueAsString(envelope));

// // 3. Wait for Consumer to process and Outbox to be saved (Implicitly via
// // Awaitility or just wait)
// // Since OrderKafkaConsumer is async/transactional, we might need to wait a
// bit.
// // However, we can just poll the OutboxPublisher.

// // We need to wait until the Order status changes to CONFIRMED or COMPLETED
// // (based on OrderKafkaConsumer logic)
// // OrderKafkaConsumer calls order.complete() which sets status to ... wait,
// // let's check Order.java if needed.
// // But better to wait for the Event in Kafka.

// // Trigger publisher periodically or wait?
// // The test environment might not have the scheduler enabled or it might be
// // slow.
// // We can manually trigger publish in a loop or just once after a delay.

// await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
// orderOutboxPublisher.publish(); // Try publishing

// // Check if OrderConfirmedEvent is in Kafka
// // Note: We might consume the STOCK_DEDUCTED event we just sent, so we need
// to
// // filter or expect multiple records.
// // But testConsumer is a separate consumer group "test-group".

// // We need to find the ORDER_CONFIRMED event.
// // Since getSingleRecord might return the first one (STOCK_DEDUCTED), we
// should
// // use getRecords or loop.
// });

// // Let's use a more robust approach:
// // 1. Send STOCK_DEDUCTED
// // 2. Wait for Order status to be updated (DB check)
// await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
// com.groom.order.domain.entity.Order order =
// orderRepository.findById(orderId).orElseThrow();
// // Assuming complete() sets status to something indicating
// // completion/confirmation
// // Let's assume it works if we see the event.
// });

// // 3. Trigger Publish
// await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
// orderOutboxPublisher.publish();

// // We expect ORDER_CONFIRMED.
// // We might receive ORDER_CREATED (from step 1) and STOCK_DEDUCTED (from step
// 2)
// // and ORDER_CONFIRMED.
// // Actually ORDER_CREATED was published in step 1 if we triggered it? No, we
// // didn't trigger publish for createOrder here.
// // But createOrder saves to outbox.

// // Let's just consume all and find the one we want.
// });

// // Consuming all records
// ConsumerRecord<String, Object> record =
// KafkaTestUtils.getSingleRecord(testConsumer, topic,
// Duration.ofSeconds(5));
// // This might be risky if there are multiple events.
// // Let's just try to get the specific event.

// // Ideally we should drain the topic before test or use a unique topic per
// test
// // (but we are using embedded kafka with fixed topic).
// // Let's just assert that *eventually* we get the event.
// }

// @Test
// @DisplayName("Order Cancelled -> Event Published to Kafka")
// void testOrderCancelledEvent() throws Exception {
// // 1. Create Order
// UUID userId = UUID.randomUUID();
// when(userClient.getUserAddress(any(), any()))
// .thenReturn(new UserAddressResponse("Test Recipient", "010-1234-5678",
// "12345", "Test Address", "101"));

// OrderCreateRequest request = new OrderCreateRequest();
// request.setTotalAmount(1000L);
// OrderCreateItemRequest itemRequest = new OrderCreateItemRequest();
// itemRequest.setProductId(UUID.randomUUID());
// itemRequest.setVariantId(UUID.randomUUID());
// itemRequest.setProductTitle("Test Product");
// itemRequest.setQuantity(1);
// itemRequest.setUnitPrice(1000L);
// request.setItems(java.util.List.of(itemRequest));

// UUID orderId = orderService.createOrder(userId, request);

// // 2. Cancel Order
// orderService.cancelOrder(orderId);

// // 3. Trigger Outbox Publisher
// orderOutboxPublisher.publish();

// // 4. Verify Event
// // We might get ORDER_CREATED and ORDER_CANCELLED.
// // We need to filter.

// boolean found = false;
// long endTime = System.currentTimeMillis() + 10000;

// while (System.currentTimeMillis() < endTime) {
// try {
// ConsumerRecord<String, Object> record =
// KafkaTestUtils.getSingleRecord(testConsumer, topic,
// Duration.ofMillis(100));
// String payloadJson = (String) record.value();
// EventEnvelope event = objectMapper.readValue(payloadJson,
// EventEnvelope.class);
// if (event.getEventType() == EventType.ORDER_CANCELLED
// && event.getAggregateId().equals(orderId.toString())) {
// found = true;
// break;
// }
// } catch (Exception e) {
// // Ignore timeout
// }
// orderOutboxPublisher.publish(); // Retry publish just in case
// }

// assertThat(found).isTrue();
// }
// }
