package com.groom.payment.application.event.payload;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedPayload {
    private String version;
    private UUID orderId;
    private String paymentKey;
    private long amount;
    private Instant paidAt;
    // private String method; // CARD, NAVER_PAY 등
}
