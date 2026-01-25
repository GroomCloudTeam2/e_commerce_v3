package com.groom.cart.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.groom.common.presentation.advice.CustomException;
import com.groom.common.presentation.advice.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groom.cart.application.dto.ProductCartInfo;
import com.groom.cart.domain.model.CartItem;
import com.groom.cart.domain.repository.CartRepository;
import com.groom.cart.infrastructure.feign.ProductClient;
import com.groom.cart.presentation.dto.request.CartAddRequest;
import com.groom.cart.presentation.dto.response.CartItemResponse;


@ExtendWith(MockitoExtension.class)
class  CartServiceTest {

    @InjectMocks
    private CartService cartService;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductClient productClient;

    private UUID userId;
    private UUID productId;
    private UUID variantId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
        variantId = UUID.randomUUID();
    }

    // =========================
    // addItemToCart
    // =========================

    @Test
    void addItemToCart_success() {
        CartAddRequest request = new CartAddRequest(productId, variantId, 2);

        ProductCartInfo product = mock(ProductCartInfo.class);
        when(product.isAvailable()).thenReturn(true);
        when(product.getStockQuantity()).thenReturn(10);

        when(productClient.getProductCartInfos(any()))
            .thenReturn(List.of(product));

        when(cartRepository.findItem(userId, productId, variantId))
            .thenReturn(Optional.empty());

        cartService.addItemToCart(userId, request);

        verify(cartRepository).addItem(userId, productId, variantId, 2);
    }

    @Test
    void addItemToCart_productNotFound() {
        CartAddRequest request = new CartAddRequest(productId, variantId, 1);

        when(productClient.getProductCartInfos(any()))
            .thenReturn(List.of());

        assertThatThrownBy(() ->
            cartService.addItemToCart(userId, request)
        )
            .isInstanceOf(CustomException.class)
            .satisfies(ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND)
            );
    }

    @Test
    void addItemToCart_notOnSale() {
        CartAddRequest request = new CartAddRequest(productId, variantId, 1);

        ProductCartInfo product = mock(ProductCartInfo.class);
        when(product.isAvailable()).thenReturn(false);

        when(productClient.getProductCartInfos(any()))
            .thenReturn(List.of(product));

        assertThatThrownBy(() ->
            cartService.addItemToCart(userId, request)
        )
            .isInstanceOf(CustomException.class)
            .satisfies(ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE)
            );
    }

    @Test
    void addItemToCart_stockNotEnough() {
        CartAddRequest request = new CartAddRequest(productId, variantId, 5);

        ProductCartInfo product = mock(ProductCartInfo.class);
        when(product.isAvailable()).thenReturn(true);
        when(product.getStockQuantity()).thenReturn(3);

        when(productClient.getProductCartInfos(any()))
            .thenReturn(List.of(product));

        when(cartRepository.findItem(userId, productId, variantId))
            .thenReturn(Optional.of(new CartItem(productId, variantId, 1)));

        assertThatThrownBy(() ->
            cartService.addItemToCart(userId, request)
        )
            .isInstanceOf(CustomException.class)
            .satisfies(ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.STOCK_NOT_ENOUGH)
            );
    }


    // =========================
    // getMyCart
    // =========================

    @Test
    void getMyCart_empty() {
        when(cartRepository.findAll(userId))
            .thenReturn(List.of());

        List<CartItemResponse> result = cartService.getMyCart(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void getMyCart_success() {
        CartItem item = new CartItem(productId, variantId, 2);

        when(cartRepository.findAll(userId))
            .thenReturn(List.of(item));

        ProductCartInfo info = mock(ProductCartInfo.class);
        when(info.getProductId()).thenReturn(productId);
        when(info.getVariantId()).thenReturn(variantId);
        when(info.getProductName()).thenReturn("상품");
        when(info.getOptionName()).thenReturn("옵션");
        when(info.getThumbnailUrl()).thenReturn("url");
        when(info.getPrice()).thenReturn(1000);
        when(info.getStockQuantity()).thenReturn(10);
        when(info.isAvailable()).thenReturn(true);

        when(productClient.getProductCartInfos(any()))
            .thenReturn(List.of(info));

        List<CartItemResponse> result = cartService.getMyCart(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalPrice()).isEqualTo(2000);
    }

    // =========================
    // updateItemQuantity
    // =========================

    @Test
    void updateItemQuantity_success() {
        CartItem item = new CartItem(productId, variantId, 1);

        when(cartRepository.findItem(userId, productId, variantId))
            .thenReturn(Optional.of(item));

        ProductCartInfo product = mock(ProductCartInfo.class);
        when(product.isAvailable()).thenReturn(true);
        when(product.getStockQuantity()).thenReturn(10);

        when(productClient.getProductCartInfos(any()))
            .thenReturn(List.of(product));

        cartService.updateItemQuantity(userId, productId, variantId, 3);

        verify(cartRepository)
            .updateQuantity(userId, productId, variantId, 3);
    }
    @Test
    void updateItemQuantity_cartItemNotFound() {
        when(cartRepository.findItem(any(), any(), any()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            cartService.updateItemQuantity(userId, productId, variantId, 1)
        )
            .isInstanceOf(CustomException.class)
            .satisfies(ex ->
                assertThat(((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND)
            );
    }


    // =========================
    // delete / clear
    // =========================

    @Test
    void deleteCartItem_success() {
        when(cartRepository.findItem(userId, productId, variantId))
            .thenReturn(Optional.of(new CartItem(productId, variantId, 1)));

        cartService.deleteCartItem(userId, productId, variantId);

        verify(cartRepository).removeItem(userId, productId, variantId);
    }

    @Test
    void clearCart_success() {
        cartService.clearCart(userId);
        verify(cartRepository).clear(userId);
    }
}
