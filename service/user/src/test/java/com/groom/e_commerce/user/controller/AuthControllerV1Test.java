package com.groom.e_commerce.user.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.e_commerce.global.presentation.advice.CustomException;
import com.groom.e_commerce.global.presentation.advice.ErrorCode;
import com.groom.e_commerce.user.application.service.AuthServiceV1;
import com.groom.e_commerce.user.domain.entity.user.UserRole;
import com.groom.e_commerce.user.presentation.controller.AuthControllerV1;
import com.groom.e_commerce.user.presentation.dto.request.user.ReqLoginDtoV1;
import com.groom.e_commerce.user.presentation.dto.request.user.ReqSignupDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.user.ResTokenDtoV1;

@WebMvcTest(AuthControllerV1.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthControllerV1 단위 테스트")
class AuthControllerV1Test {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private AuthServiceV1 authService;

	private static final String BASE_URL = "/api/v1/auth";

	@Nested
	@DisplayName("POST /api/v1/auth/signup - 회원가입")
	class SignupTest {

		@Test
		@DisplayName("일반 사용자 회원가입 성공 - 201 반환")
		void signup_User_Success() throws Exception {
			// given
			ReqSignupDtoV1 request = new ReqSignupDtoV1();
			request.setEmail("test@example.com");
			request.setPassword("password123");
			request.setNickname("testUser");
			request.setPhoneNumber("010-1234-5678");
			request.setRole(UserRole.USER);

			willDoNothing().given(authService).signup(any(ReqSignupDtoV1.class));

			// when & then
			mockMvc.perform(post(BASE_URL + "/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isCreated());

			then(authService).should().signup(any(ReqSignupDtoV1.class));
		}

		@Test
		@DisplayName("OWNER 회원가입 성공 - 201 반환")
		void signup_Owner_Success() throws Exception {
			// given
			ReqSignupDtoV1 request = new ReqSignupDtoV1();
			request.setEmail("owner@example.com");
			request.setPassword("password123");
			request.setNickname("ownerUser");
			request.setPhoneNumber("010-9999-8888");
			request.setRole(UserRole.OWNER);
			request.setStore("테스트 스토어");
			request.setZipCode("12345");
			request.setAddress("서울시 강남구");
			request.setDetailAddress("테스트빌딩 101호");
			request.setBank("신한은행");
			request.setAccount("110-123-456789");
			request.setApprovalRequest("승인 부탁드립니다.");

			willDoNothing().given(authService).signup(any(ReqSignupDtoV1.class));

			// when & then
			mockMvc.perform(post(BASE_URL + "/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isCreated());
		}

		@Test
		@DisplayName("중복 이메일로 회원가입 시 예외 발생")
		void signup_DuplicateEmail_ThrowsException() throws Exception {
			// given
			ReqSignupDtoV1 request = new ReqSignupDtoV1();
			request.setEmail("duplicate@example.com");
			request.setPassword("password123");
			request.setNickname("testUser");
			request.setPhoneNumber("010-1234-5678");
			request.setRole(UserRole.USER);

			willThrow(new CustomException(ErrorCode.EMAIL_DUPLICATED))
				.given(authService).signup(any(ReqSignupDtoV1.class));

			// when & then
			mockMvc.perform(post(BASE_URL + "/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isConflict());
		}

		@Test
		@DisplayName("중복 닉네임으로 회원가입 시 예외 발생")
		void signup_DuplicateNickname_ThrowsException() throws Exception {
			// given
			ReqSignupDtoV1 request = new ReqSignupDtoV1();
			request.setEmail("test@example.com");
			request.setPassword("password123");
			request.setNickname("duplicateNickname");
			request.setPhoneNumber("010-1234-5678");
			request.setRole(UserRole.USER);

			willThrow(new CustomException(ErrorCode.NICKNAME_DUPLICATED))
				.given(authService).signup(any(ReqSignupDtoV1.class));

			// when & then
			mockMvc.perform(post(BASE_URL + "/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isConflict());
		}

		@Test
		@DisplayName("MANAGER 역할로 회원가입 시도 시 예외 발생")
		void signup_Manager_ThrowsException() throws Exception {
			// given
			ReqSignupDtoV1 request = new ReqSignupDtoV1();
			request.setEmail("test@example.com");
			request.setPassword("password123");
			request.setNickname("testUser");
			request.setPhoneNumber("010-1234-5678");
			request.setRole(UserRole.MANAGER);

			willThrow(new CustomException(ErrorCode.VALIDATION_ERROR))
				.given(authService).signup(any(ReqSignupDtoV1.class));

			// when & then
			mockMvc.perform(post(BASE_URL + "/signup")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/auth/login - 로그인")
	class LoginTest {

		@Test
		@DisplayName("로그인 성공 - 토큰 반환")
		void login_Success() throws Exception {
			// given
			ReqLoginDtoV1 request = new ReqLoginDtoV1();
			request.setEmail("test@example.com");
			request.setPassword("password123");

			ResTokenDtoV1 response = ResTokenDtoV1.builder()
				.accessToken("access-token-value")
				.refreshToken("refresh-token-value")
				.build();

			given(authService.login(any(ReqLoginDtoV1.class))).willReturn(response);

			// when & then
			mockMvc.perform(post(BASE_URL + "/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access-token-value"))
				.andExpect(jsonPath("$.refreshToken").value("refresh-token-value"));

			then(authService).should().login(any(ReqLoginDtoV1.class));
		}

		@Test
		@DisplayName("존재하지 않는 이메일로 로그인 시 404 에러")
		void login_UserNotFound_Returns404() throws Exception {
			// given
			ReqLoginDtoV1 request = new ReqLoginDtoV1();
			request.setEmail("nonexistent@example.com");
			request.setPassword("password123");

			given(authService.login(any(ReqLoginDtoV1.class)))
				.willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

			// when & then
			mockMvc.perform(post(BASE_URL + "/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("잘못된 비밀번호로 로그인 시 401 에러")
		void login_WrongPassword_Returns401() throws Exception {
			// given
			ReqLoginDtoV1 request = new ReqLoginDtoV1();
			request.setEmail("test@example.com");
			request.setPassword("wrongPassword");

			given(authService.login(any(ReqLoginDtoV1.class)))
				.willThrow(new CustomException(ErrorCode.INVALID_PASSWORD));

			// when & then
			mockMvc.perform(post(BASE_URL + "/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isUnauthorized());
		}

		@Test
		@DisplayName("탈퇴한 사용자 로그인 시 에러")
		void login_WithdrawnUser_ReturnsError() throws Exception {
			// given
			ReqLoginDtoV1 request = new ReqLoginDtoV1();
			request.setEmail("withdrawn@example.com");
			request.setPassword("password123");

			given(authService.login(any(ReqLoginDtoV1.class)))
				.willThrow(new CustomException(ErrorCode.ALREADY_WITHDRAWN));

			// when & then
			mockMvc.perform(post(BASE_URL + "/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().is4xxClientError());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/auth/logout - 로그아웃")
	class LogoutTest {

		@Test
		@DisplayName("로그아웃 성공")
		void logout_Success() throws Exception {
			// given
			willDoNothing().given(authService).logout();

			// when & then
			mockMvc.perform(post(BASE_URL + "/logout"))
				.andDo(print())
				.andExpect(status().isOk());

			then(authService).should().logout();
		}
	}
}
