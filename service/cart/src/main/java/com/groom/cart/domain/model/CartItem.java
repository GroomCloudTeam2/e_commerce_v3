package com.groom.cart.domain.model;

import java.util.UUID;

import com.groom.cart.application.dto.StockManagement;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Cart 도메인에서 사용하는 장바구니 아이템 DTO
 * (Redis / Hash / TTL 모름)
 *
 *
*/
@Getter
@AllArgsConstructor
public class CartItem {

    private UUID productId;
    private UUID variantId;
    private int quantity;

    public StockManagement toStockManagement() {
        return StockManagement.of(productId, variantId, quantity);
    }
}
