package com.groom.order.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.common.event.Type.EventType;
import com.groom.common.event.envelope.EventEnvelope;
import com.groom.common.event.payload.OrderConfirmedPayload;
import com.groom.common.event.payload.PaymentCompletedPayload;
import com.groom.common.event.payload.PaymentFailedPayload;
import com.groom.common.event.payload.RefundFailedPayload;
import com.groom.common.event.payload.RefundSucceededPayload;
import com.groom.common.event.payload.StockDeductedPayload;
import com.groom.common.event.payload.StockDeductionFailedPayload;
import com.groom.order.domain.entity.Order;
import com.groom.order.domain.repository.OrderRepository;
import com.groom.order.domain.status.OrderStatus;
import com.groom.order.infrastructure.kafka.OrderOutboxService;

/**
 * OrderKafkaConsumer 단위 테스트
 * 
 * Kafka 이벤트 처리 로직을 Mock을 사용하여 검증합니다.
 * 예외 발생 시 DefaultErrorHandler가 재시도/DLT 처리를 할 수 있도록
 * 예외가 전파되는지 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderKafkaConsumer 테스트")
@Tag("Integration")
class OrderKafkaConsumerTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderOutboxService outboxService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderKafkaConsumer orderKafkaConsumer;

    private UUID orderId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("PAYMENT_COMPLETED 이벤트 처리 테스트")
    class HandlePaymentCompletedTest {

        @Test
        @DisplayName("PAYMENT_COMPLETED 이벤트 수신 시 Order 상태가 PAID로 변경되어야 한다")
        void handlePaymentCompleted_ShouldChange_OrderStatus_ToPaid() throws Exception {
            // given
            Order order = createOrder(OrderStatus.PENDING);
            EventEnvelope envelope = createEnvelope(EventType.PAYMENT_COMPLETED, "service-payment");
            PaymentCompletedPayload payload = PaymentCompletedPayload.builder()
                    .orderId(orderId)
                    .paymentKey("test-payment-key")
                    .amount(50000L)
                    .build();

            given(objectMapper.readValue(envelope.getPayload(), PaymentCompletedPayload.class))
                    .willReturn(payload);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            orderKafkaConsumer.handle(envelope);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            then(orderRepository).should(times(1)).save(order);
        }

        @Test
        @DisplayName("존재하지 않는 Order에 대한 PAYMENT_COMPLETED 이벤트 수신 시 예외가 전파되어야 한다")
        void handlePaymentCompleted_WhenOrderNotFound_ShouldThrowException() throws Exception {
            // given
            EventEnvelope envelope = createEnvelope(EventType.PAYMENT_COMPLETED, "service-payment");
            PaymentCompletedPayload payload = PaymentCompletedPayload.builder()
                    .orderId(orderId)
                    .paymentKey("test-payment-key")
                    .amount(50000L)
                    .build();

            given(objectMapper.readValue(envelope.getPayload(), PaymentCompletedPayload.class))
                    .willReturn(payload);
            given(orderRepository.findById(orderId)).willReturn(Optional.empty());

            // when & then - 예외가 전파되어 DefaultErrorHandler가 재시도/DLT 처리
            assertThatThrownBy(() -> orderKafkaConsumer.handle(envelope))
                    .isInstanceOf(IllegalStateException.class);
            then(orderRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("STOCK_DEDUCTED 이벤트 처리 테스트")
    class HandleStockDeductedTest {

        @Test
        @DisplayName("STOCK_DEDUCTED 이벤트 수신 시 Order 상태가 CONFIRMED로 변경되어야 한다")
        void handleStockDeducted_ShouldChange_OrderStatus_ToConfirmed() throws Exception {
            // given
            Order order = createOrder(OrderStatus.PAID);
            EventEnvelope envelope = createEnvelope(EventType.STOCK_DEDUCTED, "service-product");
            UUID productId = UUID.randomUUID();
            StockDeductedPayload.DeductedItem item = StockDeductedPayload.DeductedItem.builder()
                    .productId(productId)
                    .variantId(UUID.randomUUID())
                    .quantity(1)
                    .remainingStock(10)
                    .build();
            StockDeductedPayload payload = StockDeductedPayload.builder()
                    .orderId(orderId)
                    .items(List.of(item))
                    .build();

            given(objectMapper.readValue(envelope.getPayload(), StockDeductedPayload.class))
                    .willReturn(payload);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            orderKafkaConsumer.handle(envelope);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            then(orderRepository).should(times(1)).save(order);
        }

        @Test
        @DisplayName("STOCK_DEDUCTED 이벤트 수신 시 ORDER_CONFIRMED 이벤트를 Outbox에 저장해야 한다")
        void handleStockDeducted_ShouldSave_OrderConfirmedEvent_ToOutbox() throws Exception {
            // given
            Order order = createOrder(OrderStatus.PAID);
            EventEnvelope envelope = createEnvelope(EventType.STOCK_DEDUCTED, "service-product");
            UUID productId = UUID.randomUUID();
            StockDeductedPayload.DeductedItem item = StockDeductedPayload.DeductedItem.builder()
                    .productId(productId)
                    .variantId(UUID.randomUUID())
                    .quantity(1)
                    .remainingStock(10)
                    .build();
            StockDeductedPayload payload = StockDeductedPayload.builder()
                    .orderId(orderId)
                    .items(List.of(item))
                    .build();

            given(objectMapper.readValue(envelope.getPayload(), StockDeductedPayload.class))
                    .willReturn(payload);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            orderKafkaConsumer.handle(envelope);

            // then
            then(outboxService).should(times(1)).save(
                    eq(EventType.ORDER_CONFIRMED),
                    eq("ORDER"),
                    eq(orderId),
                    eq(orderId.toString()),
                    argThat((OrderConfirmedPayload p) -> {
                        assertThat(p.getOrderId()).isEqualTo(orderId);
                        assertThat(p.getUserId()).isEqualTo(order.getBuyerId());
                        assertThat(p.getConfirmedAt()).isNotNull();
                        return true;
                    }));
        }
    }

    @Nested
    @DisplayName("PAYMENT_FAILED 이벤트 처리 테스트")
    class HandlePaymentFailedTest {

        @Test
        @DisplayName("PAYMENT_FAILED 이벤트 수신 시 Order 상태가 FAILED로 변경되어야 한다")
        void handlePaymentFailed_ShouldChange_OrderStatus_ToFailed() throws Exception {
            // given
            Order order = createOrder(OrderStatus.PENDING);
            EventEnvelope envelope = createEnvelope(EventType.PAYMENT_FAILED, "service-payment");
            PaymentFailedPayload payload = PaymentFailedPayload.builder()
                    .orderId(orderId)
                    .paymentKey("test-payment-key")
                    .amount(50000L)
                    .failCode("INSUFFICIENT_BALANCE")
                    .failMessage("잔액 부족")
                    .build();

            given(objectMapper.readValue(envelope.getPayload(), PaymentFailedPayload.class))
                    .willReturn(payload);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            orderKafkaConsumer.handle(envelope);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            then(orderRepository).should(times(1)).save(order);
        }

        @Test
        @DisplayName("PAYMENT_FAILED 이벤트 수신 시 ORDER_CONFIRMED 이벤트는 발행하지 않아야 한다")
        void handlePaymentFailed_ShouldNot_SaveOrderConfirmedEvent() throws Exception {
            // given
            Order order = createOrder(OrderStatus.PENDING);
            EventEnvelope envelope = createEnvelope(EventType.PAYMENT_FAILED, "service-payment");
            PaymentFailedPayload payload = PaymentFailedPayload.builder()
                    .orderId(orderId)
                    .paymentKey("test-payment-key")
                    .amount(50000L)
                    .failCode("INSUFFICIENT_BALANCE")
                    .failMessage("잔액 부족")
                    .build();

            given(objectMapper.readValue(envelope.getPayload(), PaymentFailedPayload.class))
                    .willReturn(payload);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            orderKafkaConsumer.handle(envelope);

            // then
            then(outboxService).should(never()).save(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("STOCK_DEDUCTION_FAILED 이벤트 처리 테스트")
    class HandleStockDeductionFailedTest {

        @Test
        @DisplayName("STOCK_DEDUCTION_FAILED 이벤트 수신 시 Order 상태가 FAILED로 변경되어야 한다")
        void handleStockDeductionFailed_ShouldChange_OrderStatus_ToFailed() throws Exception {
            // given
            Order order = createOrder(OrderStatus.PAID);
            EventEnvelope envelope = createEnvelope(EventType.STOCK_DEDUCTION_FAILED, "service-product");
            UUID productId = UUID.randomUUID();
            StockDeductionFailedPayload.FailedItem failedItem = StockDeductionFailedPayload.FailedItem.builder()
                    .productId(productId)
                    .variantId(UUID.randomUUID())
                    .requestedQuantity(5)
                    .availableStock(2)
                    .reason("재고 부족")
                    .build();
            StockDeductionFailedPayload payload = StockDeductionFailedPayload.builder()
                    .orderId(orderId)
                    .failReason("재고 부족")
                    .failedItems(List.of(failedItem))
                    .build();

            given(objectMapper.readValue(envelope.getPayload(), StockDeductionFailedPayload.class))
                    .willReturn(payload);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            orderKafkaConsumer.handle(envelope);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
            then(orderRepository).should(times(1)).save(order);
        }
    }

    @Nested
    @DisplayName("REFUND_SUCCEEDED 이벤트 처리 테스트")
    class HandleRefundSucceededTest {

        @Test
        @DisplayName("REFUND_SUCCEEDED 이벤트 수신 시 Order 상태가 CANCELLED로 변경되어야 한다")
        void handleRefundSucceeded_ShouldChange_OrderStatus_ToCancelled() throws Exception {
            // given
            Order order = createOrder(OrderStatus.PAID);
            EventEnvelope envelope = createEnvelope(EventType.REFUND_SUCCEEDED, "service-payment");
            RefundSucceededPayload payload = RefundSucceededPayload.builder()
                    .orderId(orderId)
                    .paymentKey("test-payment-key")
                    .cancelAmount(50000L)
                    .build();

            given(objectMapper.readValue(envelope.getPayload(), RefundSucceededPayload.class))
                    .willReturn(payload);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            orderKafkaConsumer.handle(envelope);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderRepository).should(times(1)).save(order);
        }
    }

    @Nested
    @DisplayName("REFUND_FAILED 이벤트 처리 테스트")
    class HandleRefundFailedTest {

        @Test
        @DisplayName("REFUND_FAILED 이벤트 수신 시 Order 상태가 MANUAL_CHECK로 변경되어야 한다")
        void handleRefundFailed_ShouldChange_OrderStatus_ToManualCheck() throws Exception {
            // given
            Order order = createOrder(OrderStatus.PAID);
            EventEnvelope envelope = createEnvelope(EventType.REFUND_FAILED, "service-payment");
            RefundFailedPayload payload = RefundFailedPayload.builder()
                    .orderId(orderId)
                    .paymentKey("test-payment-key")
                    .cancelAmount(50000L)
                    .failCode("REFUND_FAILED")
                    .failMessage("환불 실패")
                    .build();

            given(objectMapper.readValue(envelope.getPayload(), RefundFailedPayload.class))
                    .willReturn(payload);
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            // when
            orderKafkaConsumer.handle(envelope);

            // then
            assertThat(order.getStatus()).isEqualTo(OrderStatus.MANUAL_CHECK);
            then(orderRepository).should(times(1)).save(order);
        }
    }

    @Nested
    @DisplayName("처리되지 않는 이벤트 타입 테스트")
    class HandleUnhandledEventTypeTest {

        @Test
        @DisplayName("처리되지 않는 이벤트 타입은 무시하고 정상 반환해야 한다")
        void handleUnhandledEventType_ShouldIgnore() {
            // given
            EventEnvelope envelope = createEnvelope(EventType.ORDER_CREATED, "service-other");

            // when
            orderKafkaConsumer.handle(envelope);

            // then
            then(orderRepository).should(never()).findById(any());
            then(orderRepository).should(never()).save(any());
            then(outboxService).should(never()).save(any(), any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("예외 전파 테스트 (DefaultErrorHandler 연동)")
    class ExceptionPropagationTest {

        @Test
        @DisplayName("Payload 역직렬화 실패 시 예외가 전파되어야 한다 (DefaultErrorHandler가 재시도/DLT 처리)")
        void whenPayloadDeserializationFails_ShouldThrowException() throws Exception {
            // given
            EventEnvelope envelope = createEnvelope(EventType.PAYMENT_COMPLETED, "service-payment");

            given(objectMapper.readValue(envelope.getPayload(), PaymentCompletedPayload.class))
                    .willThrow(new JsonProcessingException("Invalid JSON") {
                    });

            // when & then
            assertThatThrownBy(() -> orderKafkaConsumer.handle(envelope))
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(JsonProcessingException.class);
        }

        @Test
        @DisplayName("DB 오류 시 예외가 전파되어야 한다 (DefaultErrorHandler가 재시도/DLT 처리)")
        void whenDatabaseErrorOccurs_ShouldThrowException() throws Exception {
            // given
            EventEnvelope envelope = createEnvelope(EventType.PAYMENT_COMPLETED, "service-payment");
            PaymentCompletedPayload payload = PaymentCompletedPayload.builder()
                    .orderId(orderId)
                    .paymentKey("test-payment-key")
                    .amount(50000L)
                    .build();

            given(objectMapper.readValue(envelope.getPayload(), PaymentCompletedPayload.class))
                    .willReturn(payload);
            given(orderRepository.findById(orderId))
                    .willThrow(new RuntimeException("Database error"));

            // when & then
            assertThatThrownBy(() -> orderKafkaConsumer.handle(envelope))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Database error");
        }
    }

    // ========== Helper Methods ==========

    private Order createOrder(OrderStatus status) {
        Order order = Order.builder()
                .buyerId(userId)
                .orderNumber("20260204-123456")
                .totalPaymentAmount(50000L)
                .recipientName("홍길동")
                .recipientPhone("010-1234-5678")
                .zipCode("12345")
                .shippingAddress("서울시 강남구 테헤란로 123")
                .shippingMemo("부재 시 문 앞에 놓아주세요")
                .build();

        // Set orderId using reflection
        try {
            java.lang.reflect.Field orderIdField = Order.class.getDeclaredField("orderId");
            orderIdField.setAccessible(true);
            orderIdField.set(order, orderId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Set status to the desired value
        if (status != OrderStatus.PENDING) {
            switch (status) {
                case PENDING -> {
                    /* Already PENDING */ }
                case PAID -> order.confirmPayment();
                case CONFIRMED -> {
                    order.confirmPayment();
                    order.complete();
                }
                case FAILED -> order.fail();
                case CANCELLED -> order.cancel();
                case MANUAL_CHECK -> order.requireManualCheck();
            }
        }

        return order;
    }

    private EventEnvelope createEnvelope(EventType eventType, String producer) {
        return EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .aggregateType("ORDER")
                .aggregateId(orderId.toString())
                .producer(producer)
                .payload("{\"orderId\":\"" + orderId + "\"}")
                .build();
    }
}
