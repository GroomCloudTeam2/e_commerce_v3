package com.groom.e_commerce.user.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.groom.e_commerce.global.presentation.advice.CustomException;
import com.groom.e_commerce.global.presentation.advice.ErrorCode;
import com.groom.e_commerce.user.application.service.AdminServiceV1;
import com.groom.e_commerce.user.domain.entity.owner.OwnerStatus;
import com.groom.e_commerce.user.presentation.controller.AdminControllerV1;
import com.groom.e_commerce.user.presentation.dto.request.admin.ReqCreateManagerDtoV1;
import com.groom.e_commerce.user.presentation.dto.request.owner.ReqRejectOwnerDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.admin.ResOwnerApprovalListDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.owner.ResOwnerApprovalDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.user.ResUserListDtoV1;

// Security 설정 클래스가 있다면 Import 추가
// @Import(SecurityConfig.class)
@WebMvcTest(AdminControllerV1.class)
@Import(AdminControllerV1Test.TestSecurityConfig.class)
class AdminControllerV1Test {

	@TestConfiguration
	@EnableMethodSecurity
	static class TestSecurityConfig {

		@Bean
		SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
			http
				.csrf(csrf -> csrf.disable()) // 그러면 .with(csrf()) 없어도 됨 (있어도 상관없음)
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.exceptionHandling(e ->
					e.authenticationEntryPoint(
						new org.springframework.security.web.authentication.HttpStatusEntryPoint(
							org.springframework.http.HttpStatus.UNAUTHORIZED
						)
					)
				)
				.authorizeHttpRequests(auth -> auth
					// 회원 목록/제재: MANAGER or MASTER
					.requestMatchers("/api/v1/admin/users/**").hasAnyRole("MANAGER", "MASTER")

					// 매니저 관리: MASTER only
					.requestMatchers("/api/v1/admin/managers/**").hasRole("MASTER")

					// 오너 승인/거절: MANAGER only (너 테스트 기준)
					.requestMatchers("/api/v1/admin/owners/**").hasRole("MANAGER")

					.anyRequest().authenticated()
				);

			return http.build();
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private AdminServiceV1 adminService;

	private static final String BASE_URL = "/api/v1/admin";

	private UUID userId;
	private UUID ownerId;
	private UUID managerId;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		ownerId = UUID.randomUUID();
		managerId = UUID.randomUUID();
	}

	// ==================== 회원 관리 ====================

	@Nested
	@DisplayName("GET /api/v1/admin/users - 회원 목록 조회")
	class GetUserListTest {

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("MANAGER가 회원 목록 조회 성공")
		void getUserList_Manager_Success() throws Exception {
			// given
			ResUserListDtoV1 response = ResUserListDtoV1.builder()
				.totalElements(0L)
				.totalPages(0)
				.build();

			given(adminService.getUserList(any(Pageable.class))).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/users")
					.param("page", "0")
					.param("size", "20"))
				.andDo(print())
				.andExpect(status().isOk());

			then(adminService).should().getUserList(any(Pageable.class));
		}

		@Test
		@WithMockUser(roles = "MASTER")
		@DisplayName("MASTER가 회원 목록 조회 성공")
		void getUserList_Master_Success() throws Exception {
			// given
			ResUserListDtoV1 response = ResUserListDtoV1.builder()
				.totalElements(0L)
				.totalPages(0)
				.build();

			given(adminService.getUserList(any(Pageable.class))).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/users")
					.param("page", "0")
					.param("size", "20"))
				.andDo(print())
				.andExpect(status().isOk());
		}

		@Test
		@WithMockUser(roles = "USER")
		@DisplayName("일반 USER가 회원 목록 조회 시 403 에러")
		void getUserList_User_Returns403() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/users")
					.param("page", "0")
					.param("size", "20"))
				.andDo(print())
				.andExpect(status().isForbidden());
		}

		@Test
		@DisplayName("인증 없이 회원 목록 조회 시 401 에러")
		void getUserList_Unauthenticated_Returns401() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/users")
					.param("page", "0")
					.param("size", "20"))
				.andDo(print())
				.andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/admin/users/{userId}/ban - 회원 제재")
	class BanUserTest {

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("MANAGER가 회원 제재 성공")
		void banUser_Manager_Success() throws Exception {
			// given
			willDoNothing().given(adminService).banUser(userId);

			// when & then
			mockMvc.perform(post(BASE_URL + "/users/" + userId + "/ban")
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isOk());

			then(adminService).should().banUser(userId);
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("MANAGER 계정 제재 시도 시 403 에러")
		void banUser_ManagerTarget_Returns403() throws Exception {
			// given
			willThrow(new CustomException(ErrorCode.FORBIDDEN))
				.given(adminService).banUser(userId);

			// when & then
			mockMvc.perform(post(BASE_URL + "/users/" + userId + "/ban")
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("이미 제재된 사용자 재제재 시 에러")
		void banUser_AlreadyBanned_ReturnsError() throws Exception {
			// given
			willThrow(new CustomException(ErrorCode.VALIDATION_ERROR))
				.given(adminService).banUser(userId);

			// when & then
			mockMvc.perform(post(BASE_URL + "/users/" + userId + "/ban")
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("존재하지 않는 사용자 제재 시 404 에러")
		void banUser_NotFound_Returns404() throws Exception {
			// given
			willThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
				.given(adminService).banUser(userId);

			// when & then
			mockMvc.perform(post(BASE_URL + "/users/" + userId + "/ban")
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isNotFound());
		}

		@Test
		@WithMockUser(roles = "USER")
		@DisplayName("일반 USER가 제재 시도 시 403 에러")
		void banUser_User_Returns403() throws Exception {
			// when & then
			mockMvc.perform(post(BASE_URL + "/users/" + userId + "/ban")
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/admin/users/{userId}/unban - 회원 제재 해제")
	class UnbanUserTest {

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("제재 해제 성공")
		void unbanUser_Success() throws Exception {
			// given
			willDoNothing().given(adminService).unbanUser(userId);

			// when & then
			mockMvc.perform(post(BASE_URL + "/users/" + userId + "/unban")
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isOk());

			then(adminService).should().unbanUser(userId);
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("제재되지 않은 사용자 해제 시도 시 에러")
		void unbanUser_NotBanned_ReturnsError() throws Exception {
			// given
			willThrow(new CustomException(ErrorCode.VALIDATION_ERROR))
				.given(adminService).unbanUser(userId);

			// when & then
			mockMvc.perform(post(BASE_URL + "/users/" + userId + "/unban")
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}
	}

	// ==================== Manager 관리 ====================

	@Nested
	@DisplayName("POST /api/v1/admin/managers - Manager 계정 생성")
	class CreateManagerTest {

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("MANAGER가 Manager 생성 시도 시 403 에러")
		void createManager_Manager_Returns403() throws Exception {
			// given
			ReqCreateManagerDtoV1 request = new ReqCreateManagerDtoV1();
			request.setEmail("newmanager@example.com");
			request.setPassword("password123");
			request.setNickname("newManager");
			request.setPhoneNumber("010-9999-8888");

			// when & then
			mockMvc.perform(post(BASE_URL + "/managers")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser(roles = "MASTER")
		@DisplayName("중복 이메일로 Manager 생성 시 409 에러")
		void createManager_DuplicateEmail_Returns409() throws Exception {
			// given
			ReqCreateManagerDtoV1 request = new ReqCreateManagerDtoV1();
			request.setEmail("existing@example.com");
			request.setPassword("password123");
			request.setNickname("newManager");
			request.setPhoneNumber("010-9999-8888");

			given(adminService.createManager(any(ReqCreateManagerDtoV1.class)))
				.willThrow(new CustomException(ErrorCode.EMAIL_DUPLICATED));

			// when & then
			mockMvc.perform(post(BASE_URL + "/managers")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isConflict());
		}

		@Test
		@WithMockUser(roles = "MASTER")
		@DisplayName("필수 필드 누락 시 400 에러")
		void createManager_MissingField_Returns400() throws Exception {
			// given - 이메일 누락
			ReqCreateManagerDtoV1 request = new ReqCreateManagerDtoV1();
			request.setPassword("password123");
			request.setNickname("newManager");

			// when & then
			mockMvc.perform(post(BASE_URL + "/managers")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/admin/managers/{managerId} - Manager 삭제")
	class DeleteManagerTest {

		@Test
		@WithMockUser(roles = "MASTER")
		@DisplayName("MASTER가 Manager 삭제 성공")
		void deleteManager_Master_Success() throws Exception {
			// given
			willDoNothing().given(adminService).deleteManager(managerId);

			// when & then
			mockMvc.perform(delete(BASE_URL + "/managers/" + managerId)
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isNoContent());

			then(adminService).should().deleteManager(managerId);
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("MANAGER가 Manager 삭제 시도 시 403 에러")
		void deleteManager_Manager_Returns403() throws Exception {
			// when & then
			mockMvc.perform(delete(BASE_URL + "/managers/" + managerId)
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isForbidden());
		}

		@Test
		@WithMockUser(roles = "MASTER")
		@DisplayName("Manager가 아닌 계정 삭제 시도 시 에러")
		void deleteManager_NotManager_ReturnsError() throws Exception {
			// given
			willThrow(new CustomException(ErrorCode.VALIDATION_ERROR))
				.given(adminService).deleteManager(managerId);

			// when & then
			mockMvc.perform(delete(BASE_URL + "/managers/" + managerId)
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/managers - Manager 목록 조회")
	class GetManagerListTest {

		@Test
		@WithMockUser(roles = "MASTER")
		@DisplayName("MASTER가 Manager 목록 조회 성공")
		void getManagerList_Master_Success() throws Exception {
			// given
			ResUserListDtoV1 response = ResUserListDtoV1.builder()
				.totalElements(0L)
				.totalPages(0)
				.build();

			given(adminService.getManagerList(any(Pageable.class))).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/managers")
					.param("page", "0")
					.param("size", "20"))
				.andDo(print())
				.andExpect(status().isOk());

			then(adminService).should().getManagerList(any(Pageable.class));
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("MANAGER가 Manager 목록 조회 시 403 에러")
		void getManagerList_Manager_Returns403() throws Exception {
			// when & then
			mockMvc.perform(get(BASE_URL + "/managers")
					.param("page", "0")
					.param("size", "20"))
				.andDo(print())
				.andExpect(status().isForbidden());
		}
	}

	// ==================== Owner 승인 관리 ====================

	@Nested
	@DisplayName("GET /api/v1/admin/owners/pending - 승인 대기 Owner 목록 조회")
	class GetPendingOwnerListTest {

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("승인 대기 Owner 목록 조회 성공")
		void getPendingOwnerList_Success() throws Exception {
			// given
			ResOwnerApprovalListDtoV1 response = ResOwnerApprovalListDtoV1.builder()
				.content(List.of())
				.totalElements(0L)
				.totalPages(0)
				.build();

			given(adminService.getPendingOwnerList(any(Pageable.class))).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/owners/pending")
					.param("page", "0")
					.param("size", "20"))
				.andDo(print())
				.andExpect(status().isOk());

			then(adminService).should().getPendingOwnerList(any(Pageable.class));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/owners - 상태별 Owner 목록 조회")
	class GetOwnerListByStatusTest {

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("상태별 Owner 목록 조회 성공")
		void getOwnerListByStatus_Success() throws Exception {
			// given
			ResOwnerApprovalListDtoV1 response = ResOwnerApprovalListDtoV1.builder()
				.content(List.of())
				.totalElements(0L)
				.totalPages(0)
				.build();

			given(adminService.getOwnerListByStatus(eq(OwnerStatus.APPROVED), any(Pageable.class)))
				.willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/owners")
					.param("status", "APPROVED")
					.param("page", "0")
					.param("size", "20"))
				.andDo(print())
				.andExpect(status().isOk());

			then(adminService).should().getOwnerListByStatus(eq(OwnerStatus.APPROVED), any(Pageable.class));
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("status 파라미터 없으면 기본값 PENDING으로 조회")
		void getOwnerListByStatus_DefaultPending() throws Exception {
			// given
			ResOwnerApprovalListDtoV1 response = ResOwnerApprovalListDtoV1.builder()
				.content(List.of())
				.totalElements(0L)
				.totalPages(0)
				.build();

			given(adminService.getOwnerListByStatus(eq(OwnerStatus.PENDING), any(Pageable.class)))
				.willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/owners")
					.param("page", "0")
					.param("size", "20"))
				.andDo(print())
				.andExpect(status().isOk());

			then(adminService).should().getOwnerListByStatus(eq(OwnerStatus.PENDING), any(Pageable.class));
		}
	}

	@Nested
	@DisplayName("GET /api/v1/admin/owners/{ownerId} - Owner 승인 요청 상세 조회")
	class GetOwnerApprovalDetailTest {

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("Owner 상세 조회 성공")
		void getOwnerApprovalDetail_Success() throws Exception {
			// given
			ResOwnerApprovalDtoV1 response = ResOwnerApprovalDtoV1.builder()
				.ownerId(ownerId)
				.storeName("테스트 스토어")
				.ownerStatus(OwnerStatus.PENDING)
				.build();

			given(adminService.getOwnerApprovalDetail(ownerId)).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL + "/owners/" + ownerId))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.storeName").value("테스트 스토어"))
				.andExpect(jsonPath("$.ownerStatus").value("PENDING"));

			then(adminService).should().getOwnerApprovalDetail(ownerId);
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("존재하지 않는 Owner 조회 시 404 에러")
		void getOwnerApprovalDetail_NotFound_Returns404() throws Exception {
			// given
			given(adminService.getOwnerApprovalDetail(ownerId))
				.willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

			// when & then
			mockMvc.perform(get(BASE_URL + "/owners/" + ownerId))
				.andDo(print())
				.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/admin/owners/{ownerId}/approve - Owner 승인")
	class ApproveOwnerTest {

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("Owner 승인 성공")
		void approveOwner_Success() throws Exception {
			// given
			ResOwnerApprovalDtoV1 response = ResOwnerApprovalDtoV1.builder()
				.ownerId(ownerId)
				.storeName("테스트 스토어")
				.ownerStatus(OwnerStatus.APPROVED)
				.approvedAt(LocalDateTime.now())
				.build();

			given(adminService.approveOwner(ownerId)).willReturn(response);

			// when & then
			mockMvc.perform(post(BASE_URL + "/owners/" + ownerId + "/approve")
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerStatus").value("APPROVED"));

			then(adminService).should().approveOwner(ownerId);
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("이미 승인된 Owner 재승인 시 에러")
		void approveOwner_AlreadyApproved_ReturnsError() throws Exception {
			// given
			given(adminService.approveOwner(ownerId))
				.willThrow(new CustomException(ErrorCode.VALIDATION_ERROR));

			// when & then
			mockMvc.perform(post(BASE_URL + "/owners/" + ownerId + "/approve")
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}

		@Test
		@WithMockUser(roles = "USER")
		@DisplayName("일반 USER가 승인 시도 시 403 에러")
		void approveOwner_User_Returns403() throws Exception {
			// when & then
			mockMvc.perform(post(BASE_URL + "/owners/" + ownerId + "/approve")
					.with(csrf()))
				.andDo(print())
				.andExpect(status().isForbidden());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/admin/owners/{ownerId}/reject - Owner 거절")
	class RejectOwnerTest {

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("Owner 거절 성공")
		void rejectOwner_Success() throws Exception {
			// given
			ReqRejectOwnerDtoV1 request = new ReqRejectOwnerDtoV1();
			request.setRejectedReason("서류 미비");

			ResOwnerApprovalDtoV1 response = ResOwnerApprovalDtoV1.builder()
				.ownerId(ownerId)
				.storeName("테스트 스토어")
				.ownerStatus(OwnerStatus.REJECTED)
				.rejectedReason("서류 미비")
				.rejectedAt(LocalDateTime.now())
				.build();

			given(adminService.rejectOwner(eq(ownerId), eq("서류 미비"))).willReturn(response);

			// when & then
			mockMvc.perform(post(BASE_URL + "/owners/" + ownerId + "/reject")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerStatus").value("REJECTED"))
				.andExpect(jsonPath("$.rejectedReason").value("서류 미비"));

			then(adminService).should().rejectOwner(eq(ownerId), eq("서류 미비"));
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("이미 승인된 Owner 거절 시 에러")
		void rejectOwner_AlreadyApproved_ReturnsError() throws Exception {
			// given
			ReqRejectOwnerDtoV1 request = new ReqRejectOwnerDtoV1();
			request.setRejectedReason("거절 사유");

			given(adminService.rejectOwner(eq(ownerId), anyString()))
				.willThrow(new CustomException(ErrorCode.VALIDATION_ERROR));

			// when & then
			mockMvc.perform(post(BASE_URL + "/owners/" + ownerId + "/reject")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}

		@Test
		@WithMockUser(roles = "MANAGER")
		@DisplayName("거절 사유 누락 시 400 에러")
		void rejectOwner_MissingReason_Returns400() throws Exception {
			// given
			ReqRejectOwnerDtoV1 request = new ReqRejectOwnerDtoV1();
			// rejectedReason 누락

			// when & then
			mockMvc.perform(post(BASE_URL + "/owners/" + ownerId + "/reject")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isBadRequest());
		}

		@Test
		@WithMockUser(roles = "USER")
		@DisplayName("일반 USER가 거절 시도 시 403 에러")
		void rejectOwner_User_Returns403() throws Exception {
			// given
			ReqRejectOwnerDtoV1 request = new ReqRejectOwnerDtoV1();
			request.setRejectedReason("거절 사유");

			// when & then
			mockMvc.perform(post(BASE_URL + "/owners/" + ownerId + "/reject")
					.with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isForbidden());
		}
	}
}
