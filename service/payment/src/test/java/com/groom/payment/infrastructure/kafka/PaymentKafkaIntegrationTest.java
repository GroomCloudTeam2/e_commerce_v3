package com.groom.payment.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.common.event.payload.OrderCreatedPayload;
import com.groom.payment.domain.entity.Payment;
import com.groom.payment.domain.model.PaymentStatus;
import com.groom.payment.domain.repository.PaymentRepository;
import com.groom.payment.infrastructure.executor.TossPaymentExecutor;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = { "${event.kafka.topic:domain-events}" })
@ActiveProfiles("test")
class PaymentKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TossPaymentExecutor tossPaymentExecutor;

    @Value("${event.kafka.topic:domain-events}")
    private String topic;

    private KafkaTemplate<String, Object> testKafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafkaBroker);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, Object> pf = new DefaultKafkaProducerFactory<>(producerProps);
        testKafkaTemplate = new KafkaTemplate<>(pf);
    }

    @Test
    @DisplayName("Payment Flow: Order Created -> Payment Ready")
    void testOrderCreatedToPaymentReady() throws Exception {
        // 1. Simulate Order Created Event
        UUID orderId = UUID.randomUUID();
        OrderCreatedPayload payload = OrderCreatedPayload.builder()
                .orderId(orderId)
                .userId(1L)
                .totalAmount(1000L)
                .products(java.util.List.of())
                .build();

        EventEnvelope event = EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(EventType.ORDER_CREATED)
                .aggregateType("ORDER")
                .aggregateId(orderId.toString())
                .occurredAt(Instant.now())
                .producer("service-order")
                .payload(objectMapper.writeValueAsString(payload))
                .build();

        testKafkaTemplate.send(topic, orderId.toString(), objectMapper.writeValueAsString(event));

        // 2. Verify Payment Created in READY status
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            assertThat(payment).isNotNull();
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(payment.getAmount()).isEqualTo(1000L);
        });
    }
}
