package com.groom.common.event.payload;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmedItemPayload {
    private UUID productId;
    private UUID ownerId;
    private Long subtotal;
    private Integer quantity;
}
