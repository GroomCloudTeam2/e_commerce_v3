package com.groom.product.product.presentation.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReserveStockRequest {
    @NotNull
    private UUID orderId;

    @NotEmpty
    @Valid
    private List<ReserveStockItem> items;

    public ReserveStockRequest(UUID orderId, List<ReserveStockItem> items) {
        this.orderId = orderId;
        this.items = items;
    }
}
