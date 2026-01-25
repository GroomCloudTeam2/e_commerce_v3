package com.groom.cart.application.event.listener;

import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groom.cart.application.CartService;
import com.groom.cart.application.event.OrderConfirmedEvent;

@ExtendWith(MockitoExtension.class)
class CartOrderEventListenerTest {

	@InjectMocks
	private CartOrderEventListener listener;

	@Mock
	private CartService cartService;

	@Test
	void handle_shouldClearCart_whenOrderConfirmedEventReceived() {
		// given
		UUID userId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();

		OrderConfirmedEvent event = new OrderConfirmedEvent(userId, orderId);

		// when
		listener.handle(event);

		// then
		verify(cartService).clearCart(userId);
	}
}
