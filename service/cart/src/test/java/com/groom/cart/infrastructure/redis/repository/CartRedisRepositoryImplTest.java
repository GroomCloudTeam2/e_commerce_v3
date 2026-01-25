//package com.groom.cart.infrastructure.redis.repository;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//import java.util.List;
//import java.util.Optional;
//import java.util.UUID;
//
//import org.junit.jupiter.api.AfterEach;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Tag;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.context.annotation.Import;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.containers.GenericContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//import org.testcontainers.utility.DockerImageName;
//
//import com.groom.cart.domain.model.CartItem;
//import com.groom.cart.domain.model.CartItemKey;
//import com.groom.cart.domain.repository.CartRepository;
//import com.groom.cart.infrastructure.redis.support.CartRedisKeyGenerator;
//
//@Tag("integration")
//@DataRedisTest
//@Import({CartRedisRepositoryImpl.class, CartRedisKeyGenerator.class})
//@Testcontainers
//class CartRedisRepositoryImplTest {
//
//	@Container
//	static GenericContainer<?> redis =
//		new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
//			.withExposedPorts(6379);
//
//	@DynamicPropertySource
//	static void redisProps(DynamicPropertyRegistry registry) {
//		registry.add("spring.redis.host", redis::getHost);
//		registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
//	}
//
//	@Autowired
//	CartRepository cartRepository;
//
//	@Autowired
//	RedisTemplate<String, Object> redisTemplate;
//
//	@Autowired
//	CartRedisKeyGenerator keyGenerator;
//
//	UUID userId;
//	UUID productId;
//	UUID variantId;
//
//	@BeforeEach
//	void setUp() {
//		userId = UUID.randomUUID();
//		productId = UUID.randomUUID();
//		variantId = UUID.randomUUID();
//	}
//
//	@AfterEach
//	void tearDown() {
//		redisTemplate.delete(keyGenerator.cartKey(userId));
//	}
//
//	@Test
//	void addItem_and_findItem() {
//		cartRepository.addItem(userId, productId, variantId, 2);
//
//		Optional<CartItem> item =
//			cartRepository.findItem(userId, productId, variantId);
//
//		assertThat(item).isPresent();
//		assertThat(item.get().getQuantity()).isEqualTo(2);
//	}
//
//	@Test
//	void addItem_shouldIncrementQuantity() {
//		cartRepository.addItem(userId, productId, variantId, 2);
//		cartRepository.addItem(userId, productId, variantId, 3);
//
//		CartItem item =
//			cartRepository.findItem(userId, productId, variantId).get();
//
//		assertThat(item.getQuantity()).isEqualTo(5);
//	}
//
//	@Test
//	void findAll_returnsAllItems() {
//		cartRepository.addItem(userId, productId, variantId, 1);
//		cartRepository.addItem(userId, UUID.randomUUID(), UUID.randomUUID(), 2);
//
//		List<CartItem> items = cartRepository.findAll(userId);
//
//		assertThat(items).hasSize(2);
//	}
//
//	@Test
//	void updateQuantity_overwrite() {
//		cartRepository.addItem(userId, productId, variantId, 5);
//
//		cartRepository.updateQuantity(userId, productId, variantId, 3);
//
//		CartItem item =
//			cartRepository.findItem(userId, productId, variantId).get();
//
//		assertThat(item.getQuantity()).isEqualTo(3);
//	}
//
//	@Test
//	void updateQuantity_zero_shouldRemoveItem() {
//		cartRepository.addItem(userId, productId, variantId, 1);
//
//		cartRepository.updateQuantity(userId, productId, variantId, 0);
//
//		assertThat(
//			cartRepository.findItem(userId, productId, variantId)
//		).isEmpty();
//	}
//
//	@Test
//	void removeItem_deletesOnlyTarget() {
//		cartRepository.addItem(userId, productId, variantId, 1);
//
//		cartRepository.removeItem(userId, productId, variantId);
//
//		assertThat(
//			cartRepository.findItem(userId, productId, variantId)
//		).isEmpty();
//	}
//
//	@Test
//	void removeItems_bulkDelete() {
//		UUID p2 = UUID.randomUUID();
//		UUID v2 = UUID.randomUUID();
//
//		cartRepository.addItem(userId, productId, variantId, 1);
//		cartRepository.addItem(userId, p2, v2, 1);
//
//		cartRepository.removeItems(
//			userId,
//			List.of(
//				new CartItemKey(productId, variantId),
//				new CartItemKey(p2, v2)
//			)
//		);
//
//		assertThat(cartRepository.findAll(userId)).isEmpty();
//	}
//
//	@Test
//	void clear_removesEntireCart() {
//		cartRepository.addItem(userId, productId, variantId, 1);
//
//		cartRepository.clear(userId);
//
//		assertThat(cartRepository.findAll(userId)).isEmpty();
//	}
//}
