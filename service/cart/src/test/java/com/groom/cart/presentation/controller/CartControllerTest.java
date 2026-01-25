package com.groom.cart.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.cart.application.CartService;
import com.groom.cart.domain.model.CartItemKey;
import com.groom.cart.presentation.dto.request.CartAddRequest;
import com.groom.cart.presentation.dto.request.CartCheckoutRequest;
import com.groom.common.util.SecurityUtil;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CartController.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    private final UUID userId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();

    @Test
    void addItem_success() throws Exception {
        CartAddRequest request = new CartAddRequest(
                productId,
                variantId,
                2
        );

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(userId);

            mockMvc.perform(post("/api/v1/cart")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(cartService).addItemToCart(eq(userId), any(CartAddRequest.class));
        }
    }
    @Test
    void getMyCart_success() throws Exception {
        when(cartService.getMyCart(userId))
                .thenReturn(List.of());

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(userId);

            mockMvc.perform(get("/api/v1/cart"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON));

            verify(cartService).getMyCart(userId);
        }
    }
    @Test
    void updateQuantity_success() throws Exception {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(userId);

            mockMvc.perform(put("/api/v1/cart/items")
                            .param("productId", productId.toString())
                            .param("variantId", variantId.toString())
                            .param("quantity", "3"))
                    .andExpect(status().isNoContent());

            verify(cartService)
                    .updateItemQuantity(userId, productId, variantId, 3);
        }
    }
    @Test
    void checkout_allItems_success() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(cartService.checkout(userId)).thenReturn(orderId);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(userId);

            mockMvc.perform(post("/api/v1/cart/checkout"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.orderId").value(orderId.toString()));

            verify(cartService).checkout(userId);
        }
    }
    @Test
    void checkout_selectedItems_success() throws Exception {
        UUID orderId = UUID.randomUUID();

        List<CartItemKey> items = List.of(
                new CartItemKey(productId, variantId)
        );

        CartCheckoutRequest request = new CartCheckoutRequest();
        // setter 없으므로 reflection 사용
        ReflectionTestUtils.setField(request, "selectedItems", items);

        when(cartService.checkout(userId, items)).thenReturn(orderId);

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(userId);

            mockMvc.perform(post("/api/v1/cart/checkout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.orderId").value(orderId.toString()));

            verify(cartService).checkout(userId, items);
        }
    }

    @Test
    void deleteItem_success() throws Exception {
        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(userId);

            mockMvc.perform(delete("/api/v1/cart/items")
                            .param("productId", productId.toString())
                            .param("variantId", variantId.toString()))
                    .andExpect(status().isNoContent());

            verify(cartService).deleteCartItem(userId, productId, variantId);
        }
    }
    @Test
    void removeItems_success() throws Exception {
        List<CartService.CartItemDeleteRequest> items =
                List.of(new CartService.CartItemDeleteRequest(productId, variantId));

        try (MockedStatic<SecurityUtil> securityUtil = mockStatic(SecurityUtil.class)) {
            securityUtil.when(SecurityUtil::getCurrentUserId).thenReturn(userId);

            mockMvc.perform(delete("/api/v1/cart/items/bulk")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(items)))
                    .andExpect(status().isNoContent());

            verify(cartService).removeCartItems(eq(userId), any());
        }
    }

}
