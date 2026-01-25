package com.groom.e_commerce.user.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.groom.e_commerce.user.domain.entity.user.UserEntity;
import com.groom.e_commerce.user.domain.entity.user.UserRole;
import com.groom.e_commerce.user.domain.entity.user.UserStatus;

class UserEntityTest {

	private UserEntity user;

	@BeforeEach
	void setUp() {
		user = UserEntity.builder()
			.email("a@b.com")
			.password("pw")
			.nickname("nick")
			.phoneNumber("01012345678")
			.role(UserRole.USER)          // 네 프로젝트 enum에 맞게
			.status(UserStatus.ACTIVE)    // 네 프로젝트 enum에 맞게
			.build();
	}

	@Test
	void updateNickname_changes_value() {
		user.updateNickname("newNick");
		assertEquals("newNick", user.getNickname());
	}

	@Test
	void updateNickname_blank_throws() {
		assertThrows(IllegalArgumentException.class, () -> user.updateNickname(" "));
	}

	@Test
	void updatePhoneNumber_changes_value() {
		user.updatePhoneNumber("01099998888");
		assertEquals("01099998888", user.getPhoneNumber());
	}

	@Test
	void updatePassword_changes_value() {
		user.updatePassword("encoded");
		assertEquals("encoded", user.getPassword());
	}

	@Test
	void withdraw_sets_status_and_isWithdrawn_true() {
		user.withdraw("admin");
		assertEquals(UserStatus.WITHDRAWN, user.getStatus());
		assertTrue(user.isWithdrawn());
	}

	@Test
	void withdraw_twice_is_idempotent() {
		user.withdraw("admin");
		user.withdraw("admin2");
		assertTrue(user.isWithdrawn());
	}

	@Test
	void ban_sets_banned_and_isBanned_true() {
		user.ban();
		assertEquals(UserStatus.BANNED, user.getStatus());
		assertTrue(user.isBanned());
	}

	@Test
	void activate_from_banned_sets_active() {
		user.ban();
		user.activate();
		assertEquals(UserStatus.ACTIVE, user.getStatus());
	}

	@Test
	void activate_when_withdrawn_throws() {
		user.withdraw("admin");
		assertThrows(IllegalStateException.class, user::activate);
	}

	@Test
	void reactivate_only_when_withdrawn() {
		assertThrows(IllegalStateException.class,
			() -> user.reactivate("pw2", "nick2", "01011112222"));
	}

	@Test
	void reactivate_sets_fields_and_active() {
		user.withdraw("admin");
		user.reactivate("pw2", "nick2", "01011112222");

		assertEquals(UserStatus.ACTIVE, user.getStatus());
		assertEquals("pw2", user.getPassword());
		assertEquals("nick2", user.getNickname());
		assertEquals("01011112222", user.getPhoneNumber());
	}

	@Test
	void addresses_default_is_not_null_and_empty() {
		assertNotNull(user.getAddresses());
		assertTrue(user.getAddresses().isEmpty());
	}
}
