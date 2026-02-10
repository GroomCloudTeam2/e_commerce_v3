package com.groom.product.product.presentation.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReserveStockSingleRequest {
    @NotNull
    private UUID orderId;

    @NotNull
    private UUID productId;

    private UUID variantId;

    @Min(1)
    private int quantity;

    public ReserveStockSingleRequest(UUID orderId, UUID productId, UUID variantId, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.variantId = variantId;
        this.quantity = quantity;
    }
}
