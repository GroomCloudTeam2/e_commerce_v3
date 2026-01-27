package com.groom.product.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.common.event.payload.PaymentCompletedPayload;
import com.groom.product.product.application.dto.StockManagement;
import com.groom.product.product.application.service.ProductServiceV1;
import com.groom.product.product.infrastructure.cache.StockRedisService;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "${event.kafka.topic:domain-events}" })
@ActiveProfiles("test")
class ProductKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private StockRedisService stockRedisService;

    @MockBean
    private ProductServiceV1 productServiceV1;

    @Value("${event.kafka.topic:domain-events}")
    private String topic;

    private KafkaTemplate<String, Object> testKafkaTemplate;
    private BlockingQueue<ConsumerRecord<String, String>> records;
    private KafkaMessageListenerContainer<String, String> container;

    @BeforeEach
    void setUp() {
        // Producer Setup
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, Object> pf = new DefaultKafkaProducerFactory<>(producerProps);
        testKafkaTemplate = new KafkaTemplate<>(pf);

        // Consumer Setup (to verify Product service output)
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group-product", "false", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(consumerProps);

        ContainerProperties containerProperties = new ContainerProperties(topic);
        container = new KafkaMessageListenerContainer<>(cf, containerProperties);
        records = new LinkedBlockingQueue<>();
        container.setupMessageListener((MessageListener<String, String>) record -> {
            records.add(record);
        });
        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @Test
    @DisplayName("Product Flow: Payment Completed -> Stock Deducted")
    void testPaymentCompletedToStockDeducted() throws Exception {
        // 1. Mock Redis & Service
        UUID orderId = UUID.randomUUID();
        when(stockRedisService.getOrderStockItems(orderId)).thenReturn(List.of(
                StockManagement.builder().productId(1L).quantity(2L).build()
        ));

        // 2. Simulate Payment Completed Event
        PaymentCompletedPayload payload = PaymentCompletedPayload.builder()
                .orderId(orderId)
                .paymentKey("test-key")
                .amount(1000L)
                .build();

        EventEnvelope event = EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.PAYMENT_COMPLETED)
                .aggregateType("PAYMENT")
                .aggregateId(orderId.toString())
                .occurredAt(Instant.now())
                .producer("service-payment")
                .payload(objectMapper.writeValueAsString(payload))
                .build();

        testKafkaTemplate.send(topic, orderId.toString(), objectMapper.writeValueAsString(event));

        // 3. Verify Stock Deducted Event is published
        ConsumerRecord<String, String> received = records.poll(10, TimeUnit.SECONDS);
        assertThat(received).isNotNull();
        
        // Note: We might receive the same event we sent (PAYMENT_COMPLETED) because we are listening to the same topic.
        // We need to filter or wait for the correct event.
        
        boolean found = false;
        if (received.value().contains("STOCK_DEDUCTED")) {
            found = true;
        } else {
             // Try to get next records
             while ((received = records.poll(10, TimeUnit.SECONDS)) != null) {
                 if (received.value().contains("STOCK_DEDUCTED")) {
                     found = true;
                     break;
                 }
             }
        }
        
        assertThat(found).isTrue();
    }
}
