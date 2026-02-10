package com.groom.cart.presentation.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.groom.cart.application.CartService;
import com.groom.cart.presentation.dto.request.CartAddRequest;
import com.groom.cart.presentation.dto.request.CartCheckoutRequest;
import com.groom.cart.presentation.dto.response.CartCheckoutResponse;
import com.groom.cart.presentation.dto.response.CartItemResponse;
import com.groom.common.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Tag(name = "Cart", description = "장바구니 API")
@RestController
@RequestMapping("/api/v2/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 장바구니 담기
     *
     * 추가했자나
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void addItem(@RequestBody CartAddRequest request) {
        cartService.addItemToCart(
                SecurityUtil.getCurrentUserId(),
                request);
    }

    /**
     * 내 장바구니 조회
     */
    @GetMapping
    public List<CartItemResponse> getMyCart() {
        return cartService.getMyCart(
                SecurityUtil.getCurrentUserId());
    }

    /**
     * 수량 변경
     */
    @PutMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateQuantity(
            @RequestParam UUID productId,
            @RequestParam UUID variantId,
            @RequestParam int quantity) {
        cartService.updateItemQuantity(
                SecurityUtil.getCurrentUserId(),
                productId,
                variantId,
                quantity);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CartCheckoutResponse checkout(
            @RequestBody(required = false) CartCheckoutRequest request) {

        UUID orderId;

        if (request == null || request.getSelectedItems() == null) {
            // 전체 주문
            orderId = cartService.checkout(
                    SecurityUtil.getCurrentUserId());
        } else {
            // 선택 주문
            orderId = cartService.checkout(
                    SecurityUtil.getCurrentUserId(),
                    request.getSelectedItems());
        }

        return new CartCheckoutResponse(orderId);
    }

    /**
     *
     * 단일 아이템 삭제
     */
    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(
            @RequestParam UUID productId,
            @RequestParam UUID variantId) {
        cartService.deleteCartItem(
                SecurityUtil.getCurrentUserId(),
                productId,
                variantId);
    }

    /**
     * 여러 개 선택 삭제
     */
    @DeleteMapping("/items/bulk")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeItems(
            @RequestBody List<CartService.CartItemDeleteRequest> items) {
        cartService.removeCartItems(
                SecurityUtil.getCurrentUserId(),
                items);
    }
}
