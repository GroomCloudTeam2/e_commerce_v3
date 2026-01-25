package com.groom.e_commerce.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groom.e_commerce.global.presentation.advice.CustomException;
import com.groom.e_commerce.global.presentation.advice.ErrorCode;
import com.groom.e_commerce.user.application.service.AddressServiceV1;
import com.groom.e_commerce.user.application.service.UserServiceV1;
import com.groom.e_commerce.user.domain.entity.address.AddressEntity;
import com.groom.e_commerce.user.domain.entity.user.UserEntity;
import com.groom.e_commerce.user.domain.entity.user.UserRole;
import com.groom.e_commerce.user.domain.entity.user.UserStatus;
import com.groom.e_commerce.user.domain.repository.AddressRepository;
import com.groom.e_commerce.user.presentation.dto.request.address.ReqAddressDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.address.ResAddressDtoV1;

@ExtendWith(MockitoExtension.class)
@DisplayName("AddressServiceV1 단위 테스트")
class AddressServiceV1Test {

	@Mock
	private AddressRepository addressRepository;

	@Mock
	private UserServiceV1 userService;

	@InjectMocks
	private AddressServiceV1 addressService;

	private UUID userId;
	private UUID addressId;
	private UserEntity user;
	private AddressEntity address;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		addressId = UUID.randomUUID();

		user = UserEntity.builder()
			.userId(userId)
			.email("test@example.com")
			.password("encodedPassword")
			.nickname("testUser")
			.phoneNumber("010-1234-5678")
			.role(UserRole.USER)
			.status(UserStatus.ACTIVE)
			.build();

		address = AddressEntity.builder()
			.addressId(addressId)
			.user(user)
			.zipCode("12345")
			.address("서울시 강남구")
			.detailAddress("테스트빌딩 101호")
			.recipient("홍길동")
			.recipientPhone("010-1111-2222")
			.isDefault(false)
			.build();
	}

	// Helper methods
	private ReqAddressDtoV1 createAddressRequest(boolean isDefault) {
		ReqAddressDtoV1 request = new ReqAddressDtoV1();
		request.setZipCode("12345");
		request.setAddress("서울시 강남구");
		request.setDetailAddress("테스트빌딩 101호");
		request.setRecipient("홍길동");
		request.setRecipientPhone("010-1111-2222");
		request.setIsDefault(isDefault);
		return request;
	}

	@Nested
	@DisplayName("getAddresses 메서드")
	class GetAddressesTest {

		@Test
		@DisplayName("사용자의 모든 주소 조회 성공")
		void getAddresses_Success() {
			// given
			AddressEntity address2 = AddressEntity.builder()
				.addressId(UUID.randomUUID())
				.user(user)
				.zipCode("67890")
				.address("서울시 서초구")
				.detailAddress("다른빌딩 202호")
				.recipient("김철수")
				.recipientPhone("010-3333-4444")
				.isDefault(true)
				.build();

			List<AddressEntity> addresses = Arrays.asList(address, address2);

			given(addressRepository.findByUserUserId(userId))
				.willReturn(addresses);

			// when
			List<ResAddressDtoV1> result = addressService.getAddresses(userId);

			// then
			assertThat(result).hasSize(2);
			verify(addressRepository).findByUserUserId(userId);
		}

		@Test
		@DisplayName("주소가 없는 사용자 조회 시 빈 리스트 반환")
		void getAddresses_EmptyList() {
			// given
			given(addressRepository.findByUserUserId(userId))
				.willReturn(List.of());

			// when
			List<ResAddressDtoV1> result = addressService.getAddresses(userId);

			// then
			assertThat(result).isEmpty();
		}
	}

	@Nested
	@DisplayName("createAddress 메서드")
	class CreateAddressTest {

		@Test
		@DisplayName("새 주소 생성 성공")
		void createAddress_Success() {
			// given
			ReqAddressDtoV1 request = createAddressRequest(false);

			given(userService.findUserById(userId)).willReturn(user);

			// when
			addressService.createAddress(userId, request);

			// then
			ArgumentCaptor<AddressEntity> captor = ArgumentCaptor.forClass(AddressEntity.class);
			verify(addressRepository).save(captor.capture());

			AddressEntity savedAddress = captor.getValue();
			assertThat(savedAddress.getZipCode()).isEqualTo(request.getZipCode());
			assertThat(savedAddress.getAddress()).isEqualTo(request.getAddress());
			assertThat(savedAddress.getDetailAddress()).isEqualTo(request.getDetailAddress());
			assertThat(savedAddress.getRecipient()).isEqualTo(request.getRecipient());
			assertThat(savedAddress.getRecipientPhone()).isEqualTo(request.getRecipientPhone());
			assertThat(savedAddress.getIsDefault()).isFalse();
		}

		@Test
		@DisplayName("기본 배송지로 새 주소 생성 시 기존 기본 배송지 해제")
		void createAddress_AsDefault_ClearsExistingDefault() {
			// given
			ReqAddressDtoV1 request = createAddressRequest(true);

			given(userService.findUserById(userId)).willReturn(user);

			// when
			addressService.createAddress(userId, request);

			// then
			verify(addressRepository).clearDefaultAddress(userId);
			verify(addressRepository).save(any(AddressEntity.class));
		}

		@Test
		@DisplayName("기본 배송지가 아닌 경우 clearDefaultAddress 호출하지 않음")
		void createAddress_NotDefault_DoesNotClearDefault() {
			// given
			ReqAddressDtoV1 request = createAddressRequest(false);

			given(userService.findUserById(userId)).willReturn(user);

			// when
			addressService.createAddress(userId, request);

			// then
			verify(addressRepository, never()).clearDefaultAddress(any());
		}
	}

	@Nested
	@DisplayName("updateAddress 메서드")
	class UpdateAddressTest {

		@Test
		@DisplayName("주소 수정 성공")
		void updateAddress_Success() {
			// given
			ReqAddressDtoV1 request = new ReqAddressDtoV1();
			request.setZipCode("99999");
			request.setAddress("부산시 해운대구");
			request.setDetailAddress("새건물 301호");
			request.setRecipient("이영희");
			request.setRecipientPhone("010-5555-6666");
			request.setIsDefault(false);

			given(addressRepository.findByAddressIdAndUserUserId(addressId, userId))
				.willReturn(Optional.of(address));

			// when
			addressService.updateAddress(userId, addressId, request);

			// then
			assertThat(address.getZipCode()).isEqualTo("99999");
			assertThat(address.getAddress()).isEqualTo("부산시 해운대구");
			assertThat(address.getDetailAddress()).isEqualTo("새건물 301호");
			assertThat(address.getRecipient()).isEqualTo("이영희");
			assertThat(address.getRecipientPhone()).isEqualTo("010-5555-6666");
		}

		@Test
		@DisplayName("기본 배송지로 수정 시 기존 기본 배송지 해제")
		void updateAddress_ToDefault_ClearsExistingDefault() {
			// given
			ReqAddressDtoV1 request = createAddressRequest(true);

			given(addressRepository.findByAddressIdAndUserUserId(addressId, userId))
				.willReturn(Optional.of(address));

			// when
			addressService.updateAddress(userId, addressId, request);

			// then
			verify(addressRepository).clearDefaultAddress(userId);
		}

		@Test
		@DisplayName("존재하지 않는 주소 수정 시 예외 발생")
		void updateAddress_NotFound_ThrowsException() {
			// given
			ReqAddressDtoV1 request = createAddressRequest(false);

			given(addressRepository.findByAddressIdAndUserUserId(addressId, userId))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> addressService.updateAddress(userId, addressId, request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADDRESS_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("deleteAddress 메서드")
	class DeleteAddressTest {

		@Test
		@DisplayName("주소 삭제 성공")
		void deleteAddress_Success() {
			// given
			given(addressRepository.findByAddressIdAndUserUserId(addressId, userId))
				.willReturn(Optional.of(address));

			// when
			addressService.deleteAddress(userId, addressId);

			// then
			verify(addressRepository).delete(address);
		}

		@Test
		@DisplayName("존재하지 않는 주소 삭제 시 예외 발생")
		void deleteAddress_NotFound_ThrowsException() {
			// given
			given(addressRepository.findByAddressIdAndUserUserId(addressId, userId))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> addressService.deleteAddress(userId, addressId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADDRESS_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("setDefaultAddress 메서드")
	class SetDefaultAddressTest {

		@Test
		@DisplayName("기본 배송지 설정 성공")
		void setDefaultAddress_Success() {
			// given
			given(addressRepository.findByAddressIdAndUserUserId(addressId, userId))
				.willReturn(Optional.of(address));

			// when
			addressService.setDefaultAddress(userId, addressId);

			// then
			verify(addressRepository).clearDefaultAddress(userId);
			assertThat(address.getIsDefault()).isTrue();
		}

		@Test
		@DisplayName("이미 기본 배송지인 주소를 기본 배송지로 설정 시 예외 발생")
		void setDefaultAddress_AlreadyDefault_ThrowsException() {
			// given
			AddressEntity defaultAddress = AddressEntity.builder()
				.addressId(addressId)
				.user(user)
				.zipCode("12345")
				.address("서울시 강남구")
				.detailAddress("테스트빌딩 101호")
				.recipient("홍길동")
				.recipientPhone("010-1111-2222")
				.isDefault(true)
				.build();

			given(addressRepository.findByAddressIdAndUserUserId(addressId, userId))
				.willReturn(Optional.of(defaultAddress));

			// when & then
			assertThatThrownBy(() -> addressService.setDefaultAddress(userId, addressId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_DEFAULT_ADDRESS);
		}

		@Test
		@DisplayName("존재하지 않는 주소를 기본 배송지로 설정 시 예외 발생")
		void setDefaultAddress_NotFound_ThrowsException() {
			// given
			given(addressRepository.findByAddressIdAndUserUserId(addressId, userId))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> addressService.setDefaultAddress(userId, addressId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADDRESS_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("getAddress 메서드")
	class GetAddressTest {

		@Test
		@DisplayName("단일 주소 조회 성공")
		void getAddress_Success() {
			// given
			given(addressRepository.findByAddressIdAndUserUserId(addressId, userId))
				.willReturn(Optional.of(address));

			// when
			ResAddressDtoV1 result = addressService.getAddress(addressId, userId);

			// then
			assertThat(result).isNotNull();
		}

		@Test
		@DisplayName("존재하지 않는 주소 조회 시 예외 발생")
		void getAddress_NotFound_ThrowsException() {
			// given
			given(addressRepository.findByAddressIdAndUserUserId(addressId, userId))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> addressService.getAddress(addressId, userId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ADDRESS_NOT_FOUND);
		}
	}
}
