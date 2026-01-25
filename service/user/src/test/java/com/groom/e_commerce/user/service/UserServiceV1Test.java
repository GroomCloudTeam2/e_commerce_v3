package com.groom.e_commerce.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groom.e_commerce.global.presentation.advice.CustomException;
import com.groom.e_commerce.global.presentation.advice.ErrorCode;
import com.groom.e_commerce.user.application.service.UserServiceV1;
import com.groom.e_commerce.user.domain.entity.address.AddressEntity;
import com.groom.e_commerce.user.domain.entity.owner.OwnerEntity;
import com.groom.e_commerce.user.domain.entity.owner.OwnerStatus;
import com.groom.e_commerce.user.domain.entity.user.PeriodType;
import com.groom.e_commerce.user.domain.entity.user.UserEntity;
import com.groom.e_commerce.user.domain.entity.user.UserRole;
import com.groom.e_commerce.user.domain.entity.user.UserStatus;
import com.groom.e_commerce.user.domain.event.UserUpdateEvent;
import com.groom.e_commerce.user.domain.event.UserWithdrawnEvent;
import com.groom.e_commerce.user.domain.repository.AddressRepository;
import com.groom.e_commerce.user.domain.repository.OwnerRepository;
import com.groom.e_commerce.user.domain.repository.UserRepository;
import com.groom.e_commerce.user.presentation.dto.request.user.ReqUpdateUserDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.user.ResUserDtoV1;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceV1 단위 테스트")
class UserServiceV1Test {

	@Mock
	private UserRepository userRepository;

	@Mock
	private AddressRepository addressRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private UserServiceV1 userService;

	private UUID userId;
	private UserEntity user;
	private AddressEntity defaultAddress;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		user = UserEntity.builder()
			.userId(userId)
			.email("test@example.com")
			.password("encodedPassword")
			.nickname("testUser")
			.phoneNumber("010-1234-5678")
			.role(UserRole.USER)
			.status(UserStatus.ACTIVE)
			.build();

		defaultAddress = AddressEntity.builder()
			.addressId(UUID.randomUUID())
			.user(user)
			.zipCode("12345")
			.address("서울시 강남구")
			.detailAddress("테스트빌딩 101호")
			.recipient("홍길동")
			.recipientPhone("010-1111-2222")
			.isDefault(true)
			.build();
	}

	@Nested
	@DisplayName("getMe 메서드")
	class GetMeTest {

		@Test
		@DisplayName("일반 사용자 정보 조회 성공")
		void getMe_Success_ForUser() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));
			given(addressRepository.findByUserUserIdAndIsDefaultTrue(userId))
				.willReturn(Optional.of(defaultAddress));

			// when
			ResUserDtoV1 result = userService.getMe(userId);

			// then
			assertThat(result).isNotNull();
			verify(userRepository).findByUserIdAndDeletedAtIsNull(userId);
			verify(addressRepository).findByUserUserIdAndIsDefaultTrue(userId);
			verify(ownerRepository, never()).findByUserUserIdAndDeletedAtIsNull(any());
		}

		@Test
		@DisplayName("OWNER 사용자 정보 조회 시 Owner 정보 포함")
		void getMe_Success_ForOwner() {
			// given
			UserEntity ownerUser = UserEntity.builder()
				.userId(userId)
				.email("owner@example.com")
				.password("encodedPassword")
				.nickname("ownerUser")
				.phoneNumber("010-9999-8888")
				.role(UserRole.OWNER)
				.status(UserStatus.ACTIVE)
				.build();

			OwnerEntity owner = OwnerEntity.builder()
				.ownerId(UUID.randomUUID())
				.user(ownerUser)
				.storeName("테스트스토어")
				.ownerStatus(OwnerStatus.APPROVED)
				.build();

			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(ownerUser));
			given(addressRepository.findByUserUserIdAndIsDefaultTrue(userId))
				.willReturn(Optional.empty());
			given(ownerRepository.findByUserUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(owner));

			// when
			ResUserDtoV1 result = userService.getMe(userId);

			// then
			assertThat(result).isNotNull();
			verify(ownerRepository).findByUserUserIdAndDeletedAtIsNull(userId);
		}

		@Test
		@DisplayName("존재하지 않는 사용자 조회 시 예외 발생")
		void getMe_UserNotFound() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> userService.getMe(userId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("updateMe 메서드")
	class UpdateMeTest {

		@Test
		@DisplayName("닉네임 업데이트 성공")
		void updateMe_Nickname_Success() {
			// given
			ReqUpdateUserDtoV1 request = new ReqUpdateUserDtoV1();
			request.setNickname("newNickname");

			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));
			given(userRepository.findByNickname("newNickname"))
				.willReturn(Optional.empty());

			// when
			userService.updateMe(userId, request);

			// then
			assertThat(user.getNickname()).isEqualTo("newNickname");
			verify(eventPublisher).publishEvent(any(UserUpdateEvent.class));
		}

		@Test
		@DisplayName("전화번호 업데이트 성공")
		void updateMe_PhoneNumber_Success() {
			// given
			ReqUpdateUserDtoV1 request = new ReqUpdateUserDtoV1();
			request.setPhoneNumber("010-9999-0000");

			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));

			// when
			userService.updateMe(userId, request);

			// then
			assertThat(user.getPhoneNumber()).isEqualTo("010-9999-0000");
			verify(eventPublisher).publishEvent(any(UserUpdateEvent.class));
		}

		@Test
		@DisplayName("비밀번호 업데이트 성공")
		void updateMe_Password_Success() {
			// given
			ReqUpdateUserDtoV1 request = new ReqUpdateUserDtoV1();
			request.setPassword("newPassword123");

			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));
			given(passwordEncoder.encode("newPassword123"))
				.willReturn("encodedNewPassword");

			// when
			userService.updateMe(userId, request);

			// then
			assertThat(user.getPassword()).isEqualTo("encodedNewPassword");
			verify(passwordEncoder).encode("newPassword123");
			verify(eventPublisher).publishEvent(any(UserUpdateEvent.class));
		}

		@Test
		@DisplayName("중복된 닉네임으로 업데이트 시 예외 발생")
		void updateMe_DuplicateNickname_ThrowsException() {
			// given
			ReqUpdateUserDtoV1 request = new ReqUpdateUserDtoV1();
			request.setNickname("existingNickname");

			UserEntity anotherUser = UserEntity.builder()
				.userId(UUID.randomUUID())
				.nickname("existingNickname")
				.build();

			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));
			given(userRepository.findByNickname("existingNickname"))
				.willReturn(Optional.of(anotherUser));

			// when & then
			assertThatThrownBy(() -> userService.updateMe(userId, request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_DUPLICATED);
		}

		@Test
		@DisplayName("업데이트 내용이 없으면 이벤트 발행하지 않음")
		void updateMe_NoChanges_NoEvent() {
			// given
			ReqUpdateUserDtoV1 request = new ReqUpdateUserDtoV1();
			// 모든 필드가 null 또는 빈 문자열

			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));

			// when
			userService.updateMe(userId, request);

			// then
			verify(eventPublisher, never()).publishEvent(any());
		}
	}

	@Nested
	@DisplayName("deleteMe 메서드")
	class DeleteMeTest {

		@Test
		@DisplayName("회원 탈퇴 성공")
		void deleteMe_Success() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));

			// when
			userService.deleteMe(userId);

			// then
			assertThat(user.isWithdrawn()).isTrue();
			verify(eventPublisher).publishEvent(any(UserWithdrawnEvent.class));
		}

		@Test
		@DisplayName("이미 탈퇴한 사용자의 탈퇴 시도 시 예외 발생")
		void deleteMe_AlreadyWithdrawn_ThrowsException() {
			// given
			user.withdraw();
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));

			// when & then
			assertThatThrownBy(() -> userService.deleteMe(userId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_WITHDRAWN);
		}
	}

	@Nested
	@DisplayName("getSalesStats 메서드")
	class GetSalesStatsTest {

		@Test
		@DisplayName("OWNER 사용자의 판매 통계 조회 성공")
		void getSalesStats_Success() {
			// given
			UserEntity ownerUser = UserEntity.builder()
				.userId(userId)
				.email("owner@example.com")
				.role(UserRole.OWNER)
				.status(UserStatus.ACTIVE)
				.build();

			LocalDate targetDate = LocalDate.of(2024, 1, 15);

			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(ownerUser));

			// when
			var result = userService.getSalesStats(userId, PeriodType.DAILY, targetDate);

			// then
			assertThat(result).isNotNull();
			assertThat(result).hasSize(1);
		}

		@Test
		@DisplayName("OWNER가 아닌 사용자의 판매 통계 조회 시 예외 발생")
		void getSalesStats_NotOwner_ThrowsException() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user)); // 일반 USER

			// when & then
			assertThatThrownBy(() -> userService.getSalesStats(userId, PeriodType.DAILY, LocalDate.now()))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
		}
	}

	@Nested
	@DisplayName("findUserById 메서드")
	class FindUserByIdTest {

		@Test
		@DisplayName("사용자 조회 성공")
		void findUserById_Success() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));

			// when
			UserEntity result = userService.findUserById(userId);

			// then
			assertThat(result).isEqualTo(user);
		}

		@Test
		@DisplayName("존재하지 않는 사용자 조회 시 예외 발생")
		void findUserById_NotFound_ThrowsException() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> userService.findUserById(userId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
		}
	}
}
