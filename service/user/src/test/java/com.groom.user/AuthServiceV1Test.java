package com.groom.user;

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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groom.common.enums.UserRole;
import com.groom.common.infrastructure.config.security.JwtUtil;
import com.groom.common.presentation.advice.CustomException;
import com.groom.common.presentation.advice.ErrorCode;
import com.groom.user.application.service.AuthServiceV1;
import com.groom.user.domain.entity.owner.OwnerEntity;
import com.groom.user.domain.entity.user.UserEntity;
import com.groom.user.domain.entity.user.UserStatus;
import com.groom.user.domain.event.OwnerSignedUpEvent;
import com.groom.user.domain.event.UserSignedUpEvent;
import com.groom.user.domain.repository.OwnerRepository;
import com.groom.user.domain.repository.UserRepository;
import com.groom.user.presentation.dto.request.user.ReqLoginDtoV1;
import com.groom.user.presentation.dto.request.user.ReqSignupDtoV1;
import com.groom.user.presentation.dto.response.user.ResTokenDtoV1;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceV1 테스트")
@AutoConfigureMockMvc(addFilters = false)
class AuthServiceV1Test {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OwnerRepository ownerRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthServiceV1 authService;

    private UUID userId;
    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        userEntity = UserEntity.builder()
                .userId(userId)
                .email("test@example.com")
                .password("encodedPassword")
                .nickname("testUser")
                .phoneNumber("010-1234-5678")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Nested
    @DisplayName("signup() 테스트")
    class SignupTest {

        @Test
        @DisplayName("일반 USER 회원가입 성공")
        void signup_User_Success() {
            ReqSignupDtoV1 request = new ReqSignupDtoV1();
            request.setEmail("newuser@example.com");
            request.setPassword("password123");
            request.setNickname("newUser");
            request.setPhoneNumber("010-1111-2222");
            request.setRole(UserRole.USER);

            given(userRepository.findByEmail("newuser@example.com"))
                    .willReturn(Optional.empty());
            given(userRepository.existsByNicknameAndDeletedAtIsNull("newUser"))
                    .willReturn(false);
            given(passwordEncoder.encode("password123"))
                    .willReturn("encodedPassword123");
            given(userRepository.save(any(UserEntity.class)))
                    .willAnswer(invocation -> {
                        UserEntity user = invocation.getArgument(0);
                        return UserEntity.builder()
                                .userId(UUID.randomUUID())
                                .email(user.getEmail())
                                .password(user.getPassword())
                                .nickname(user.getNickname())
                                .phoneNumber(user.getPhoneNumber())
                                .role(user.getRole())
                                .status(user.getStatus())
                                .build();
                    });

            authService.signup(request);

            verify(userRepository).save(any(UserEntity.class));
            verify(eventPublisher).publishEvent(any(UserSignedUpEvent.class));
        }

        @Test
        @DisplayName("OWNER 회원가입 성공")
        void signup_Owner_Success() {
            ReqSignupDtoV1 request = new ReqSignupDtoV1();
            request.setEmail("owner@example.com");
            request.setPassword("password123");
            request.setNickname("ownerUser");
            request.setPhoneNumber("010-3333-4444");
            request.setRole(UserRole.OWNER);
            request.setStore("테스트 스토어");
            request.setZipCode("12345");
            request.setAddress("서울시 강남구");
            request.setDetailAddress("테헤란로 123");

            given(userRepository.findByEmail("owner@example.com"))
                    .willReturn(Optional.empty());
            given(userRepository.existsByNicknameAndDeletedAtIsNull("ownerUser"))
                    .willReturn(false);
            given(passwordEncoder.encode("password123"))
                    .willReturn("encodedPassword123");
            given(userRepository.save(any(UserEntity.class)))
                    .willAnswer(invocation -> {
                        UserEntity user = invocation.getArgument(0);
                        return UserEntity.builder()
                                .userId(UUID.randomUUID())
                                .email(user.getEmail())
                                .password(user.getPassword())
                                .nickname(user.getNickname())
                                .phoneNumber(user.getPhoneNumber())
                                .role(user.getRole())
                                .status(user.getStatus())
                                .build();
                    });
            given(ownerRepository.save(any(OwnerEntity.class)))
                    .willAnswer(invocation -> {
                        OwnerEntity owner = invocation.getArgument(0);
                        return OwnerEntity.builder()
                                .ownerId(UUID.randomUUID())
                                .user(owner.getUser())
                                .storeName(owner.getStoreName())
                                .ownerStatus(owner.getOwnerStatus())
                                .build();
                    });

            authService.signup(request);

            verify(userRepository).save(any(UserEntity.class));
            verify(ownerRepository).save(any(OwnerEntity.class));
            verify(eventPublisher).publishEvent(any(OwnerSignedUpEvent.class));
        }

        @Test
        @DisplayName("MANAGER 역할로 회원가입 시도 시 예외 발생")
        void signup_Manager_ThrowsException() {
            ReqSignupDtoV1 request = new ReqSignupDtoV1();
            request.setEmail("manager@example.com");
            request.setPassword("password123");
            request.setNickname("managerUser");
            request.setPhoneNumber("010-5555-6666");
            request.setRole(UserRole.MANAGER);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("MASTER 역할로 회원가입 시도 시 예외 발생")
        void signup_Master_ThrowsException() {
            ReqSignupDtoV1 request = new ReqSignupDtoV1();
            request.setEmail("master@example.com");
            request.setPassword("password123");
            request.setNickname("masterUser");
            request.setPhoneNumber("010-7777-8888");
            request.setRole(UserRole.MASTER);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("중복 이메일로 회원가입 시 예외 발생")
        void signup_DuplicateEmail_ThrowsException() {
            ReqSignupDtoV1 request = new ReqSignupDtoV1();
            request.setEmail("existing@example.com");
            request.setPassword("password123");
            request.setNickname("newUser");
            request.setPhoneNumber("010-1111-2222");
            request.setRole(UserRole.USER);

            UserEntity existingUser = UserEntity.builder()
                    .userId(UUID.randomUUID())
                    .email("existing@example.com")
                    .status(UserStatus.ACTIVE)
                    .build();

            given(userRepository.findByEmail("existing@example.com"))
                    .willReturn(Optional.of(existingUser));

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_DUPLICATED);
        }

        @Test
        @DisplayName("중복 닉네임으로 회원가입 시 예외 발생")
        void signup_DuplicateNickname_ThrowsException() {
            ReqSignupDtoV1 request = new ReqSignupDtoV1();
            request.setEmail("newuser@example.com");
            request.setPassword("password123");
            request.setNickname("existingNickname");
            request.setPhoneNumber("010-1111-2222");
            request.setRole(UserRole.USER);

            given(userRepository.findByEmail("newuser@example.com"))
                    .willReturn(Optional.empty());
            given(userRepository.existsByNicknameAndDeletedAtIsNull("existingNickname"))
                    .willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_DUPLICATED);
        }

        @Test
        @DisplayName("탈퇴한 사용자가 같은 이메일로 재가입 시 reactivate 처리")
        void signup_WithdrawnUser_Reactivate() {
            ReqSignupDtoV1 request = new ReqSignupDtoV1();
            request.setEmail("withdrawn@example.com");
            request.setPassword("newPassword123");
            request.setNickname("newNickname");
            request.setPhoneNumber("010-9999-0000");
            request.setRole(UserRole.USER);

            UserEntity withdrawnUser = UserEntity.builder()
                    .userId(UUID.randomUUID())
                    .email("withdrawn@example.com")
                    .password("oldPassword")
                    .nickname("oldNickname")
                    .phoneNumber("010-0000-0000")
                    .role(UserRole.USER)
                    .status(UserStatus.WITHDRAWN)
                    .build();

            given(userRepository.findByEmail("withdrawn@example.com"))
                    .willReturn(Optional.of(withdrawnUser));
            given(passwordEncoder.encode("newPassword123"))
                    .willReturn("encodedNewPassword");

            authService.signup(request);

            assertThat(withdrawnUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(withdrawnUser.getNickname()).isEqualTo("newNickname");
            assertThat(withdrawnUser.getPhoneNumber()).isEqualTo("010-9999-0000");
            verify(userRepository, never()).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("OWNER 회원가입 시 가게 이름이 없으면 예외 발생")
        void signup_Owner_NoStoreName_ThrowsException() {
            ReqSignupDtoV1 request = new ReqSignupDtoV1();
            request.setEmail("owner@example.com");
            request.setPassword("password123");
            request.setNickname("ownerUser");
            request.setPhoneNumber("010-3333-4444");
            request.setRole(UserRole.OWNER);
            request.setStore(null); // 가게 이름 없음

            given(userRepository.findByEmail("owner@example.com"))
                    .willReturn(Optional.empty());
            given(userRepository.existsByNicknameAndDeletedAtIsNull("ownerUser"))
                    .willReturn(false);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("OWNER 회원가입 시 가게 이름이 빈 문자열이면 예외 발생")
        void signup_Owner_EmptyStoreName_ThrowsException() {
            ReqSignupDtoV1 request = new ReqSignupDtoV1();
            request.setEmail("owner@example.com");
            request.setPassword("password123");
            request.setNickname("ownerUser");
            request.setPhoneNumber("010-3333-4444");
            request.setRole(UserRole.OWNER);
            request.setStore("   "); // 공백만 있는 가게 이름

            given(userRepository.findByEmail("owner@example.com"))
                    .willReturn(Optional.empty());
            given(userRepository.existsByNicknameAndDeletedAtIsNull("ownerUser"))
                    .willReturn(false);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
        }
    }

    @Nested
    @DisplayName("login() 테스트")
    class LoginTest {

        @Test
        @DisplayName("로그인 성공")
        void login_Success() {
            ReqLoginDtoV1 request = new ReqLoginDtoV1();
            request.setEmail("test@example.com");
            request.setPassword("password123");

            given(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
                    .willReturn(Optional.of(userEntity));
            given(passwordEncoder.matches("password123", "encodedPassword"))
                    .willReturn(true);
            given(jwtUtil.generateAccessToken(userId, "test@example.com", "USER"))
                    .willReturn("accessToken123");
            given(jwtUtil.generateRefreshToken(userId, "test@example.com", "USER"))
                    .willReturn("refreshToken123");

            ResTokenDtoV1 result = authService.login(request);

            assertThat(result).isNotNull();
            assertThat(result.getAccessToken()).isEqualTo("accessToken123");
            assertThat(result.getRefreshToken()).isEqualTo("refreshToken123");
            assertThat(result.getTokenType()).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("존재하지 않는 사용자로 로그인 시 예외 발생")
        void login_UserNotFound_ThrowsException() {
            ReqLoginDtoV1 request = new ReqLoginDtoV1();
            request.setEmail("nonexistent@example.com");
            request.setPassword("password123");

            given(userRepository.findByEmailAndDeletedAtIsNull("nonexistent@example.com"))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("잘못된 비밀번호로 로그인 시 예외 발생")
        void login_InvalidPassword_ThrowsException() {
            ReqLoginDtoV1 request = new ReqLoginDtoV1();
            request.setEmail("test@example.com");
            request.setPassword("wrongPassword");

            given(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
                    .willReturn(Optional.of(userEntity));
            given(passwordEncoder.matches("wrongPassword", "encodedPassword"))
                    .willReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_PASSWORD);
        }

        @Test
        @DisplayName("탈퇴한 사용자로 로그인 시 예외 발생")
        void login_WithdrawnUser_ThrowsException() {
            userEntity.withdraw();

            ReqLoginDtoV1 request = new ReqLoginDtoV1();
            request.setEmail("test@example.com");
            request.setPassword("password123");

            given(userRepository.findByEmailAndDeletedAtIsNull("test@example.com"))
                    .willReturn(Optional.of(userEntity));
            given(passwordEncoder.matches("password123", "encodedPassword"))
                    .willReturn(true);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(CustomException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("logout() 테스트")
    class LogoutTest {

        @Test
        @DisplayName("로그아웃 성공")
        void logout_Success() {
            // logout은 단순히 로깅만 수행하므로 예외가 발생하지 않으면 성공
            assertThatCode(() -> authService.logout()).doesNotThrowAnyException();
        }
    }
}
