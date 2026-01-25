package com.groom.e_commerce.user.entity;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groom.e_commerce.user.domain.entity.address.AddressEntity;

class AddressEntityTest {

	private AddressEntity address;

	@BeforeEach
	void setUp() {
		address = AddressEntity.builder()
			.addressId(UUID.randomUUID())
			.recipient("홍길동")
			.recipientPhone("010-1234-5678")
			.zipCode("12345")
			.address("서울시 강남구")
			.detailAddress("101호")
			.isDefault(false)
			.build();
	}

	@Nested
	@DisplayName("Builder 테스트")
	class BuilderTest {

		@Test
		@DisplayName("isDefault 기본값은 false이다")
		void isDefaultDefaultValue() {
			// when
			AddressEntity newAddress = AddressEntity.builder()
				.addressId(UUID.randomUUID())
				.zipCode("12345")
				.address("서울시")
				.detailAddress("101호")
				.build();

			// then
			assertThat(newAddress.getIsDefault()).isFalse();
		}
	}

	@Nested
	@DisplayName("update 메서드")
	class UpdateTest {

		@Test
		@DisplayName("모든 필드가 정상적으로 업데이트된다")
		void updateAllFields() {
			// when
			address.update("54321", "부산시 해운대구", "202호",
				"김철수", "010-9999-8888", true);

			// then
			assertThat(address.getZipCode()).isEqualTo("54321");
			assertThat(address.getAddress()).isEqualTo("부산시 해운대구");
			assertThat(address.getDetailAddress()).isEqualTo("202호");
			assertThat(address.getRecipient()).isEqualTo("김철수");
			assertThat(address.getRecipientPhone()).isEqualTo("010-9999-8888");
			assertThat(address.getIsDefault()).isTrue();
		}

		@Test
		@DisplayName("isDefault가 null이면 기존 값을 유지한다")
		void isDefaultNullKeepsOriginal() {
			// given
			address.setDefault(true);

			// when
			address.update("54321", "부산시 해운대구", "202호",
				"김철수", "010-9999-8888", null);

			// then
			assertThat(address.getIsDefault()).isTrue();
		}

		@Test
		@DisplayName("isDefault가 false면 false로 업데이트된다")
		void isDefaultFalseUpdate() {
			// given
			address.setDefault(true);

			// when
			address.update("54321", "부산시", "202호",
				"김철수", "010-9999-8888", false);

			// then
			assertThat(address.getIsDefault()).isFalse();
		}

		@Test
		@DisplayName("다른 필드들은 null이어도 null로 업데이트된다")
		void otherFieldsCanBeNull() {
			// when
			address.update("54321", "부산시", "202호", null, null, false);

			// then
			assertThat(address.getZipCode()).isEqualTo("54321");
			assertThat(address.getAddress()).isEqualTo("부산시");
			assertThat(address.getDetailAddress()).isEqualTo("202호");
			assertThat(address.getRecipient()).isNull();
			assertThat(address.getRecipientPhone()).isNull();
		}
	}

	@Nested
	@DisplayName("setDefault 메서드")
	class SetDefaultTest {

		@Test
		@DisplayName("기본 배송지로 설정한다")
		void setDefaultTrue() {
			// when
			address.setDefault(true);

			// then
			assertThat(address.getIsDefault()).isTrue();
		}

		@Test
		@DisplayName("기본 배송지 해제한다")
		void setDefaultFalse() {
			// given
			address.setDefault(true);

			// when
			address.setDefault(false);

			// then
			assertThat(address.getIsDefault()).isFalse();
		}
	}
}
