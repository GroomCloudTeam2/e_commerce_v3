package com.groom.payment.application.event.payload;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedPayload {
    private String version;
    private UUID orderId;
    private String paymentKey;
    private String failCode;
    private String failMessage;
}
