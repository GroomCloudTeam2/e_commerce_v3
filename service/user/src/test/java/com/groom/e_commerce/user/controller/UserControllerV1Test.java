package com.groom.e_commerce.user.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import com.groom.e_commerce.user.application.service.UserServiceV1;
import com.groom.e_commerce.user.domain.entity.user.PeriodType;
import com.groom.e_commerce.user.domain.entity.user.UserRole;
import com.groom.e_commerce.user.presentation.controller.UserControllerV1;
import com.groom.e_commerce.user.presentation.dto.request.user.ReqUpdateUserDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.owner.ResSalesStatDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.user.ResUserDtoV1;

@WebMvcTest(UserControllerV1.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserControllerV1 단위 테스트")
class UserControllerV1Test {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private UserServiceV1 userService;

	private static final String BASE_URL = "/api/v1/users/me";
	private static final String USER_ID_HEADER = "X-User-Id";
	private static final String USER_ROLE_HEADER = "X-User-Role";

	private UUID userId;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
	}

	@Nested
	@DisplayName("GET /api/v1/users/me - 내 정보 조회")
	class GetMeTest {

		@Test
		@DisplayName("내 정보 조회 성공")
		void getMe_Success() throws Exception {
			// given
			ResUserDtoV1 response = ResUserDtoV1.builder()
				.id(userId)
				.email("test@example.com")
				.nickname("testUser")
				.phoneNumber("010-1234-5678")
				.role(UserRole.USER)
				.createdAt(LocalDateTime.now())
				.build();

			given(userService.getMe(any(UUID.class))).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL)
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("test@example.com"))
				.andExpect(jsonPath("$.nickname").value("testUser"));

			then(userService).should().getMe(any(UUID.class));
		}

		@Test
		@DisplayName("OWNER 정보 조회 시 Owner 정보 포함")
		void getMe_Owner_IncludesOwnerInfo() throws Exception {
			// given
			ResUserDtoV1 response = ResUserDtoV1.builder()
				.id(userId)
				.email("owner@example.com")
				.nickname("ownerUser")
				.phoneNumber("010-9999-8888")
				.role(UserRole.OWNER)
				.createdAt(LocalDateTime.now())
				.build();

			given(userService.getMe(any(UUID.class))).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL)
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("OWNER"));
		}

		@Test
		@DisplayName("존재하지 않는 사용자 조회 시 404 에러")
		void getMe_NotFound_Returns404() throws Exception {
			// given
			given(userService.getMe(any(UUID.class)))
				.willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

			// when & then
			mockMvc.perform(get(BASE_URL)
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("PATCH /api/v1/users/me - 내 정보 수정")
	class UpdateMeTest {

		@Test
		@DisplayName("닉네임 수정 성공")
		void updateMe_Nickname_Success() throws Exception {
			// given
			ReqUpdateUserDtoV1 request = new ReqUpdateUserDtoV1();
			request.setNickname("newNickname");

			willDoNothing().given(userService).updateMe(any(UUID.class), any(ReqUpdateUserDtoV1.class));

			// when & then
			mockMvc.perform(patch(BASE_URL)
					.header(USER_ID_HEADER, userId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isOk());

			then(userService).should().updateMe(any(UUID.class), any(ReqUpdateUserDtoV1.class));
		}

		@Test
		@DisplayName("전화번호 수정 성공")
		void updateMe_PhoneNumber_Success() throws Exception {
			// given
			ReqUpdateUserDtoV1 request = new ReqUpdateUserDtoV1();
			request.setPhoneNumber("010-9999-0000");

			willDoNothing().given(userService).updateMe(any(UUID.class), any(ReqUpdateUserDtoV1.class));

			// when & then
			mockMvc.perform(patch(BASE_URL)
					.header(USER_ID_HEADER, userId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("비밀번호 수정 성공")
		void updateMe_Password_Success() throws Exception {
			// given
			ReqUpdateUserDtoV1 request = new ReqUpdateUserDtoV1();
			request.setPassword("newPassword123");

			willDoNothing().given(userService).updateMe(any(UUID.class), any(ReqUpdateUserDtoV1.class));

			// when & then
			mockMvc.perform(patch(BASE_URL)
					.header(USER_ID_HEADER, userId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("중복 닉네임으로 수정 시 409 에러")
		void updateMe_DuplicateNickname_Returns409() throws Exception {
			// given
			ReqUpdateUserDtoV1 request = new ReqUpdateUserDtoV1();
			request.setNickname("existingNickname");

			willThrow(new CustomException(ErrorCode.NICKNAME_DUPLICATED))
				.given(userService).updateMe(any(UUID.class), any(ReqUpdateUserDtoV1.class));

			// when & then
			mockMvc.perform(patch(BASE_URL)
					.header(USER_ID_HEADER, userId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isConflict());
		}

		@Test
		@DisplayName("존재하지 않는 사용자 수정 시 404 에러")
		void updateMe_NotFound_Returns404() throws Exception {
			// given
			ReqUpdateUserDtoV1 request = new ReqUpdateUserDtoV1();
			request.setNickname("newNickname");

			willThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
				.given(userService).updateMe(any(UUID.class), any(ReqUpdateUserDtoV1.class));

			// when & then
			mockMvc.perform(patch(BASE_URL)
					.header(USER_ID_HEADER, userId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/users/me - 회원 탈퇴")
	class DeleteMeTest {

		@Test
		@DisplayName("회원 탈퇴 성공")
		void deleteMe_Success() throws Exception {
			// given
			willDoNothing().given(userService).deleteMe(any(UUID.class));

			// when & then
			mockMvc.perform(delete(BASE_URL)
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isNoContent());

			then(userService).should().deleteMe(any(UUID.class));
		}

		@Test
		@DisplayName("이미 탈퇴한 사용자 재탈퇴 시 에러")
		void deleteMe_AlreadyWithdrawn_ReturnsError() throws Exception {
			// given
			willThrow(new CustomException(ErrorCode.ALREADY_WITHDRAWN))
				.given(userService).deleteMe(any(UUID.class));

			// when & then
			mockMvc.perform(delete(BASE_URL)
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().is4xxClientError());
		}

		@Test
		@DisplayName("존재하지 않는 사용자 탈퇴 시 404 에러")
		void deleteMe_NotFound_Returns404() throws Exception {
			// given
			willThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
				.given(userService).deleteMe(any(UUID.class));

			// when & then
			mockMvc.perform(delete(BASE_URL)
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/users/me/sales - 매출 통계 조회")
	class GetSalesStatsTest {

		@Test
		@DisplayName("일별 매출 통계 조회 성공")
		void getSalesStats_Daily_Success() throws Exception {
			// given
			LocalDate date = LocalDate.of(2024, 1, 15);
			List<ResSalesStatDtoV1> response = List.of(
				ResSalesStatDtoV1.builder()
					.date(date)
					.totalAmount(1000000L)
					.build()
			);

			given(userService.getSalesStats(any(UUID.class), eq(PeriodType.DAILY), eq(date)))
				.willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/sales")
					.header(USER_ID_HEADER, userId.toString())
					.header(USER_ROLE_HEADER, "OWNER")
					.param("periodType", "DAILY")
					.param("date", "2024-01-15"))
				.andDo(print())
				.andExpect(status().isOk());

			then(userService).should().getSalesStats(any(UUID.class), eq(PeriodType.DAILY), eq(date));
		}

		@Test
		@DisplayName("월별 매출 통계 조회 성공 (date 없이)")
		void getSalesStats_Monthly_WithoutDate() throws Exception {
			// given
			List<ResSalesStatDtoV1> response = List.of(
				ResSalesStatDtoV1.builder()
					.totalAmount(1000000L)
					.build()
			);

			given(userService.getSalesStats(any(UUID.class), eq(PeriodType.MONTHLY), isNull()))
				.willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/sales")
					.header(USER_ID_HEADER, userId.toString())
					.header(USER_ROLE_HEADER, "OWNER")
					.param("periodType", "MONTHLY"))
				.andDo(print())
				.andExpect(status().isOk());

			then(userService).should().getSalesStats(any(UUID.class), eq(PeriodType.MONTHLY), isNull());
		}

		@Test
		@DisplayName("OWNER가 아닌 사용자 조회 시 403 에러")
		void getSalesStats_NotOwner_Returns403() throws Exception {
			// given
			given(userService.getSalesStats(any(UUID.class), any(), any()))
				.willThrow(new CustomException(ErrorCode.FORBIDDEN));

			// when & then
			mockMvc.perform(get(BASE_URL + "/sales")
					.header(USER_ID_HEADER, userId.toString())
					.header(USER_ROLE_HEADER, "USER")
					.param("periodType", "DAILY"))
				.andDo(print())
				.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("존재하지 않는 사용자 조회 시 404 에러")
		void getSalesStats_UserNotFound_Returns404() throws Exception {
			// given
			given(userService.getSalesStats(any(UUID.class), any(), any()))
				.willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

			// when & then
			mockMvc.perform(get(BASE_URL + "/sales")
					.header(USER_ID_HEADER, userId.toString())
					.header(USER_ROLE_HEADER, "OWNER")
					.param("periodType", "DAILY"))
				.andDo(print())
				.andExpect(status().isNotFound());
		}
	}
}
