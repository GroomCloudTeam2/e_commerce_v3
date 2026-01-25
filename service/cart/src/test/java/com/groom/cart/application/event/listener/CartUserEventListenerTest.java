package com.groom.cart.application.event.listener;

import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groom.cart.application.CartService;
import com.groom.cart.application.event.UserDeletedEvent;

@ExtendWith(MockitoExtension.class)
class CartUserEventListenerTest {

	@InjectMocks
	private CartUserEventListener listener;

	@Mock
	private CartService cartService;

	@Test
	void handle_shouldClearCart_whenUserDeletedEventReceived() {
		// given
		UUID userId = UUID.randomUUID();
		UserDeletedEvent event = new UserDeletedEvent(userId);

		// when
		listener.handle(event);

		// then
		verify(cartService).clearCart(userId);
	}
}
