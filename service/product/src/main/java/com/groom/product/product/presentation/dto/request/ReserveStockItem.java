package com.groom.product.product.presentation.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReserveStockItem {
    @NotNull
    private UUID productId;

    private UUID variantId;

    @Min(1)
    private int quantity;

    public ReserveStockItem(UUID productId, UUID variantId, int quantity) {
        this.productId = productId;
        this.variantId = variantId;
        this.quantity = quantity;
    }
}
