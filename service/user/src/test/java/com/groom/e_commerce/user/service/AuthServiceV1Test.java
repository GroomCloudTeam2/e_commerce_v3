package com.groom.e_commerce.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

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

import com.groom.e_commerce.global.infrastructure.config.security.JwtUtil;
import com.groom.e_commerce.global.presentation.advice.CustomException;
import com.groom.e_commerce.global.presentation.advice.ErrorCode;
import com.groom.e_commerce.user.application.service.AuthServiceV1;
import com.groom.e_commerce.user.domain.entity.owner.OwnerEntity;
import com.groom.e_commerce.user.domain.entity.user.UserEntity;
import com.groom.e_commerce.user.domain.entity.user.UserRole;
import com.groom.e_commerce.user.domain.entity.user.UserStatus;
import com.groom.e_commerce.user.domain.event.OwnerSignedUpEvent;
import com.groom.e_commerce.user.domain.event.UserSignedUpEvent;
import com.groom.e_commerce.user.domain.repository.OwnerRepository;
import com.groom.e_commerce.user.domain.repository.UserRepository;
import com.groom.e_commerce.user.presentation.dto.request.user.ReqLoginDtoV1;
import com.groom.e_commerce.user.presentation.dto.request.user.ReqSignupDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.user.ResTokenDtoV1;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceV1 단위 테스트")
class AuthServiceV1Test {

	@Mock
	private UserRepository userRepository;

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private PasswordEncoder passwordEncoder;

	@InjectMocks
	private AuthServiceV1 authService;

	private UUID userId;
	private UserEntity user;

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
	}

	// Helper methods
	private ReqSignupDtoV1 createSignupRequest(UserRole role) {
		ReqSignupDtoV1 request = new ReqSignupDtoV1();
		request.setEmail("newuser@example.com");
		request.setPassword("password123");
		request.setNickname("newUser");
		request.setPhoneNumber("010-1234-5678");
		request.setRole(role);
		return request;
	}

	private ReqSignupDtoV1 createOwnerSignupRequest() {
		ReqSignupDtoV1 request = createSignupRequest(UserRole.OWNER);
		request.setStore("테스트스토어");
		request.setZipCode("12345");
		request.setAddress("서울시 강남구");
		request.setDetailAddress("테스트빌딩 101호");
		request.setBank("신한은행");
		request.setAccount("110-123-456789");
		request.setApprovalRequest("승인 요청드립니다.");
		return request;
	}

	@Nested
	@DisplayName("signup 메서드")
	class SignupTest {

		@Test
		@DisplayName("일반 사용자 회원가입 성공")
		void signup_User_Success() {
			// given
			ReqSignupDtoV1 request = createSignupRequest(UserRole.USER);

			given(userRepository.findByEmail(request.getEmail()))
				.willReturn(Optional.empty());
			given(userRepository.existsByNicknameAndDeletedAtIsNull(request.getNickname()))
				.willReturn(false);
			given(passwordEncoder.encode(request.getPassword()))
				.willReturn("encodedPassword");
			given(userRepository.save(any(UserEntity.class)))
				.willAnswer(invocation -> {
					UserEntity savedUser = invocation.getArgument(0);
					return UserEntity.builder()
						.userId(UUID.randomUUID())
						.email(savedUser.getEmail())
						.password(savedUser.getPassword())
						.nickname(savedUser.getNickname())
						.phoneNumber(savedUser.getPhoneNumber())
						.role(savedUser.getRole())
						.status(savedUser.getStatus())
						.build();
				});

			// when
			authService.signup(request);

			// then
			verify(userRepository).save(any(UserEntity.class));
			verify(eventPublisher).publishEvent(any(UserSignedUpEvent.class));
		}

		@Test
		@DisplayName("OWNER 회원가입 성공")
		void signup_Owner_Success() {
			// given
			ReqSignupDtoV1 request = createOwnerSignupRequest();

			given(userRepository.findByEmail(request.getEmail()))
				.willReturn(Optional.empty());
			given(userRepository.existsByNicknameAndDeletedAtIsNull(request.getNickname()))
				.willReturn(false);
			given(passwordEncoder.encode(request.getPassword()))
				.willReturn("encodedPassword");
			given(userRepository.save(any(UserEntity.class)))
				.willAnswer(invocation -> {
					UserEntity savedUser = invocation.getArgument(0);
					return UserEntity.builder()
						.userId(UUID.randomUUID())
						.email(savedUser.getEmail())
						.password(savedUser.getPassword())
						.nickname(savedUser.getNickname())
						.phoneNumber(savedUser.getPhoneNumber())
						.role(savedUser.getRole())
						.status(savedUser.getStatus())
						.build();
				});
			given(ownerRepository.save(any(OwnerEntity.class)))
				.willAnswer(invocation -> {
					OwnerEntity savedOwner = invocation.getArgument(0);
					return OwnerEntity.builder()
						.ownerId(UUID.randomUUID())
						.user(savedOwner.getUser())
						.storeName(savedOwner.getStoreName())
						.ownerStatus(savedOwner.getOwnerStatus())
						.build();
				});

			// when
			authService.signup(request);

			// then
			verify(userRepository).save(any(UserEntity.class));
			verify(ownerRepository).save(any(OwnerEntity.class));
			verify(eventPublisher).publishEvent(any(OwnerSignedUpEvent.class));
		}

		@Test
		@DisplayName("MANAGER 역할로 회원가입 시도 시 예외 발생")
		void signup_Manager_ThrowsException() {
			// given
			ReqSignupDtoV1 request = createSignupRequest(UserRole.MANAGER);

			// when & then
			assertThatThrownBy(() -> authService.signup(request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
		}

		@Test
		@DisplayName("MASTER 역할로 회원가입 시도 시 예외 발생")
		void signup_Master_ThrowsException() {
			// given
			ReqSignupDtoV1 request = createSignupRequest(UserRole.MASTER);

			// when & then
			assertThatThrownBy(() -> authService.signup(request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
		}

		@Test
		@DisplayName("이미 존재하는 이메일로 회원가입 시 예외 발생")
		void signup_DuplicateEmail_ThrowsException() {
			// given
			ReqSignupDtoV1 request = createSignupRequest(UserRole.USER);

			given(userRepository.findByEmail(request.getEmail()))
				.willReturn(Optional.of(user));

			// when & then
			assertThatThrownBy(() -> authService.signup(request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_DUPLICATED);
		}

		@Test
		@DisplayName("이미 존재하는 닉네임으로 회원가입 시 예외 발생")
		void signup_DuplicateNickname_ThrowsException() {
			// given
			ReqSignupDtoV1 request = createSignupRequest(UserRole.USER);

			given(userRepository.findByEmail(request.getEmail()))
				.willReturn(Optional.empty());
			given(userRepository.existsByNicknameAndDeletedAtIsNull(request.getNickname()))
				.willReturn(true);

			// when & then
			assertThatThrownBy(() -> authService.signup(request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_DUPLICATED);
		}

		@Test
		@DisplayName("탈퇴한 사용자가 같은 이메일로 재가입 시 계정 복구")
		void signup_ReactivateWithdrawnUser() {
			// given
			ReqSignupDtoV1 request = createSignupRequest(UserRole.USER);

			UserEntity withdrawnUser = UserEntity.builder()
				.userId(userId)
				.email(request.getEmail())
				.password("oldPassword")
				.nickname("oldNickname")
				.phoneNumber("010-0000-0000")
				.role(UserRole.USER)
				.status(UserStatus.WITHDRAWN)
				.build();

			given(userRepository.findByEmail(request.getEmail()))
				.willReturn(Optional.of(withdrawnUser));
			given(passwordEncoder.encode(request.getPassword()))
				.willReturn("newEncodedPassword");

			// when
			authService.signup(request);

			// then
			assertThat(withdrawnUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
			verify(userRepository, never()).save(any());
		}

		@Test
		@DisplayName("OWNER 가입 시 store 필드 누락 예외 발생")
		void signup_Owner_MissingStore_ThrowsException() {
			// given
			ReqSignupDtoV1 request = createSignupRequest(UserRole.OWNER);
			// store 필드가 null인 상태

			given(userRepository.findByEmail(request.getEmail()))
				.willReturn(Optional.empty());
			given(userRepository.existsByNicknameAndDeletedAtIsNull(request.getNickname()))
				.willReturn(false);

			// when & then
			assertThatThrownBy(() -> authService.signup(request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
		}
	}

	@Nested
	@DisplayName("login 메서드")
	class LoginTest {

		@Test
		@DisplayName("로그인 성공")
		void login_Success() {
			// given
			ReqLoginDtoV1 request = new ReqLoginDtoV1();
			request.setEmail("test@example.com");
			request.setPassword("password123");

			given(userRepository.findByEmailAndDeletedAtIsNull(request.getEmail()))
				.willReturn(Optional.of(user));
			given(passwordEncoder.matches(request.getPassword(), user.getPassword()))
				.willReturn(true);
			given(jwtUtil.generateAccessToken(user.getUserId(), user.getEmail(), user.getRole().name()))
				.willReturn("accessToken");
			given(jwtUtil.generateRefreshToken(user.getUserId(), user.getEmail(), user.getRole().name()))
				.willReturn("refreshToken");

			// when
			ResTokenDtoV1 result = authService.login(request);

			// then
			assertThat(result).isNotNull();
			assertThat(result.getAccessToken()).isEqualTo("accessToken");
			assertThat(result.getRefreshToken()).isEqualTo("refreshToken");
		}

		@Test
		@DisplayName("존재하지 않는 이메일로 로그인 시 예외 발생")
		void login_UserNotFound_ThrowsException() {
			// given
			ReqLoginDtoV1 request = new ReqLoginDtoV1();
			request.setEmail("nonexistent@example.com");
			request.setPassword("password123");

			given(userRepository.findByEmailAndDeletedAtIsNull(request.getEmail()))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
		}

		@Test
		@DisplayName("잘못된 비밀번호로 로그인 시 예외 발생")
		void login_InvalidPassword_ThrowsException() {
			// given
			ReqLoginDtoV1 request = new ReqLoginDtoV1();
			request.setEmail("test@example.com");
			request.setPassword("wrongPassword");

			given(userRepository.findByEmailAndDeletedAtIsNull(request.getEmail()))
				.willReturn(Optional.of(user));
			given(passwordEncoder.matches(request.getPassword(), user.getPassword()))
				.willReturn(false);

			// when & then
			assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD);
		}

		@Test
		@DisplayName("탈퇴한 사용자 로그인 시 예외 발생")
		void login_WithdrawnUser_ThrowsException() {
			// given
			ReqLoginDtoV1 request = new ReqLoginDtoV1();
			request.setEmail("test@example.com");
			request.setPassword("password123");

			user.withdraw();

			given(userRepository.findByEmailAndDeletedAtIsNull(request.getEmail()))
				.willReturn(Optional.of(user));
			given(passwordEncoder.matches(request.getPassword(), user.getPassword()))
				.willReturn(true);

			// when & then
			assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_WITHDRAWN);
		}
	}

	@Nested
	@DisplayName("logout 메서드")
	class LogoutTest {

		@Test
		@DisplayName("로그아웃 성공")
		void logout_Success() {
			// when & then
			assertThatNoException().isThrownBy(() -> authService.logout());
		}
	}
}
