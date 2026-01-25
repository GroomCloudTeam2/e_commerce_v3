package com.groom.e_commerce.user.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.groom.e_commerce.global.presentation.advice.CustomException;
import com.groom.e_commerce.global.presentation.advice.ErrorCode;
import com.groom.e_commerce.user.application.service.AdminServiceV1;
import com.groom.e_commerce.user.domain.entity.owner.OwnerEntity;
import com.groom.e_commerce.user.domain.entity.owner.OwnerStatus;
import com.groom.e_commerce.user.domain.entity.user.UserEntity;
import com.groom.e_commerce.user.domain.entity.user.UserRole;
import com.groom.e_commerce.user.domain.entity.user.UserStatus;
import com.groom.e_commerce.user.domain.repository.OwnerRepository;
import com.groom.e_commerce.user.domain.repository.UserRepository;
import com.groom.e_commerce.user.presentation.dto.request.admin.ReqCreateManagerDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.admin.ResOwnerApprovalListDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.owner.ResOwnerApprovalDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.user.ResUserDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.user.ResUserListDtoV1;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminServiceV1 단위 테스트")
class AdminServiceV1Test {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private OwnerRepository ownerRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private AdminServiceV1 adminService;

	private UUID userId;
	private UUID ownerId;
	private UserEntity user;
	private UserEntity managerUser;
	private OwnerEntity owner;
	private Pageable pageable;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		ownerId = UUID.randomUUID();
		pageable = PageRequest.of(0, 10);

		user = UserEntity.builder()
			.userId(userId)
			.email("user@example.com")
			.password("encodedPassword")
			.nickname("testUser")
			.phoneNumber("010-1234-5678")
			.role(UserRole.USER)
			.status(UserStatus.ACTIVE)
			.build();

		managerUser = UserEntity.builder()
			.userId(UUID.randomUUID())
			.email("manager@example.com")
			.password("encodedPassword")
			.nickname("testManager")
			.phoneNumber("010-9999-8888")
			.role(UserRole.MANAGER)
			.status(UserStatus.ACTIVE)
			.build();

		owner = OwnerEntity.builder()
			.ownerId(ownerId)
			.user(user)
			.storeName("테스트스토어")
			.ownerStatus(OwnerStatus.PENDING)
			.build();
	}

	@Nested
	@DisplayName("getUserList 메서드")
	class GetUserListTest {

		@Test
		@DisplayName("회원 목록 조회 성공")
		void getUserList_Success() {
			// given
			Page<UserEntity> userPage = new PageImpl<>(List.of(user), pageable, 1);

			given(userRepository.findByDeletedAtIsNull(pageable))
				.willReturn(userPage);

			// when
			ResUserListDtoV1 result = adminService.getUserList(pageable);

			// then
			assertThat(result).isNotNull();
			verify(userRepository).findByDeletedAtIsNull(pageable);
		}
	}

	@Nested
	@DisplayName("banUser 메서드")
	class BanUserTest {

		@Test
		@DisplayName("일반 사용자 제재 성공")
		void banUser_Success() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));

			// when
			adminService.banUser(userId);

			// then
			assertThat(user.isBanned()).isTrue();
		}

		@Test
		@DisplayName("MANAGER 계정 제재 시도 시 예외 발생")
		void banUser_Manager_ThrowsException() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(managerUser.getUserId()))
				.willReturn(Optional.of(managerUser));

			// when & then
			assertThatThrownBy(() -> adminService.banUser(managerUser.getUserId()))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
		}

		@Test
		@DisplayName("MASTER 계정 제재 시도 시 예외 발생")
		void banUser_Master_ThrowsException() {
			// given
			UserEntity masterUser = UserEntity.builder()
				.userId(UUID.randomUUID())
				.email("master@example.com")
				.role(UserRole.MASTER)
				.status(UserStatus.ACTIVE)
				.build();

			given(userRepository.findByUserIdAndDeletedAtIsNull(masterUser.getUserId()))
				.willReturn(Optional.of(masterUser));

			// when & then
			assertThatThrownBy(() -> adminService.banUser(masterUser.getUserId()))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
		}

		@Test
		@DisplayName("이미 제재된 사용자 제재 시도 시 예외 발생")
		void banUser_AlreadyBanned_ThrowsException() {
			// given
			user.ban();
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));

			// when & then
			assertThatThrownBy(() -> adminService.banUser(userId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
		}

		@Test
		@DisplayName("존재하지 않는 사용자 제재 시 예외 발생")
		void banUser_UserNotFound_ThrowsException() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminService.banUser(userId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("unbanUser 메서드")
	class UnbanUserTest {

		@Test
		@DisplayName("제재 해제 성공")
		void unbanUser_Success() {
			// given
			user.ban();
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));

			// when
			adminService.unbanUser(userId);

			// then
			assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		}

		@Test
		@DisplayName("제재되지 않은 사용자 해제 시도 시 예외 발생")
		void unbanUser_NotBanned_ThrowsException() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user));

			// when & then
			assertThatThrownBy(() -> adminService.unbanUser(userId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
		}
	}

	@Nested
	@DisplayName("createManager 메서드")
	class CreateManagerTest {

		@Test
		@DisplayName("Manager 계정 생성 성공")
		void createManager_Success() {
			// given
			ReqCreateManagerDtoV1 request = new ReqCreateManagerDtoV1();
			request.setEmail("newmanager@example.com");
			request.setPassword("password123");
			request.setNickname("newManager");
			request.setPhoneNumber("010-7777-8888");

			given(userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail()))
				.willReturn(false);
			given(userRepository.existsByNicknameAndDeletedAtIsNull(request.getNickname()))
				.willReturn(false);
			given(passwordEncoder.encode(request.getPassword()))
				.willReturn("encodedPassword");
			given(userRepository.save(any(UserEntity.class)))
				.willAnswer(invocation -> invocation.getArgument(0));

			// when
			ResUserDtoV1 result = adminService.createManager(request);

			// then
			assertThat(result).isNotNull();

			ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
			verify(userRepository).save(captor.capture());

			UserEntity savedManager = captor.getValue();
			assertThat(savedManager.getRole()).isEqualTo(UserRole.MANAGER);
			assertThat(savedManager.getStatus()).isEqualTo(UserStatus.ACTIVE);
		}

		@Test
		@DisplayName("중복 이메일로 Manager 생성 시 예외 발생")
		void createManager_DuplicateEmail_ThrowsException() {
			// given
			ReqCreateManagerDtoV1 request = new ReqCreateManagerDtoV1();
			request.setEmail("existing@example.com");
			request.setPassword("password123");
			request.setNickname("newManager");
			request.setPhoneNumber("010-7777-8888");

			given(userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail()))
				.willReturn(true);

			// when & then
			assertThatThrownBy(() -> adminService.createManager(request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.EMAIL_DUPLICATED);
		}

		@Test
		@DisplayName("중복 닉네임으로 Manager 생성 시 예외 발생")
		void createManager_DuplicateNickname_ThrowsException() {
			// given
			ReqCreateManagerDtoV1 request = new ReqCreateManagerDtoV1();
			request.setEmail("newmanager@example.com");
			request.setPassword("password123");
			request.setNickname("existingNickname");
			request.setPhoneNumber("010-7777-8888");

			given(userRepository.existsByEmailAndDeletedAtIsNull(request.getEmail()))
				.willReturn(false);
			given(userRepository.existsByNicknameAndDeletedAtIsNull(request.getNickname()))
				.willReturn(true);

			// when & then
			assertThatThrownBy(() -> adminService.createManager(request))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.NICKNAME_DUPLICATED);
		}
	}

	@Nested
	@DisplayName("deleteManager 메서드")
	class DeleteManagerTest {

		@Test
		@DisplayName("Manager 삭제 성공")
		void deleteManager_Success() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(managerUser.getUserId()))
				.willReturn(Optional.of(managerUser));

			// when
			adminService.deleteManager(managerUser.getUserId());

			// then
			assertThat(managerUser.isWithdrawn()).isTrue();
		}

		@Test
		@DisplayName("Manager가 아닌 계정 삭제 시도 시 예외 발생")
		void deleteManager_NotManager_ThrowsException() {
			// given
			given(userRepository.findByUserIdAndDeletedAtIsNull(userId))
				.willReturn(Optional.of(user)); // USER 역할

			// when & then
			assertThatThrownBy(() -> adminService.deleteManager(userId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
		}
	}

	@Nested
	@DisplayName("getManagerList 메서드")
	class GetManagerListTest {

		@Test
		@DisplayName("Manager 목록 조회 성공")
		void getManagerList_Success() {
			// given
			Page<UserEntity> managerPage = new PageImpl<>(List.of(managerUser), pageable, 1);

			given(userRepository.findByRoleAndDeletedAtIsNull(UserRole.MANAGER, pageable))
				.willReturn(managerPage);

			// when
			ResUserListDtoV1 result = adminService.getManagerList(pageable);

			// then
			assertThat(result).isNotNull();
			verify(userRepository).findByRoleAndDeletedAtIsNull(UserRole.MANAGER, pageable);
		}
	}

	@Nested
	@DisplayName("getPendingOwnerList 메서드")
	class GetPendingOwnerListTest {

		@Test
		@DisplayName("승인 대기 Owner 목록 조회 성공")
		void getPendingOwnerList_Success() {
			// given
			Page<OwnerEntity> ownerPage = new PageImpl<>(List.of(owner), pageable, 1);

			given(ownerRepository.findByOwnerStatusAndDeletedAtIsNull(OwnerStatus.PENDING, pageable))
				.willReturn(ownerPage);

			// when
			ResOwnerApprovalListDtoV1 result = adminService.getPendingOwnerList(pageable);

			// then
			assertThat(result).isNotNull();
			verify(ownerRepository).findByOwnerStatusAndDeletedAtIsNull(OwnerStatus.PENDING, pageable);
		}
	}

	@Nested
	@DisplayName("getOwnerListByStatus 메서드")
	class GetOwnerListByStatusTest {

		@Test
		@DisplayName("상태별 Owner 목록 조회 성공")
		void getOwnerListByStatus_Success() {
			// given
			Page<OwnerEntity> ownerPage = new PageImpl<>(List.of(owner), pageable, 1);

			given(ownerRepository.findByOwnerStatusAndDeletedAtIsNull(OwnerStatus.APPROVED, pageable))
				.willReturn(ownerPage);

			// when
			ResOwnerApprovalListDtoV1 result = adminService.getOwnerListByStatus(OwnerStatus.APPROVED, pageable);

			// then
			assertThat(result).isNotNull();
			verify(ownerRepository).findByOwnerStatusAndDeletedAtIsNull(OwnerStatus.APPROVED, pageable);
		}
	}

	@Nested
	@DisplayName("getOwnerApprovalDetail 메서드")
	class GetOwnerApprovalDetailTest {

		@Test
		@DisplayName("Owner 승인 요청 상세 조회 성공")
		void getOwnerApprovalDetail_Success() {
			// given
			given(ownerRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
				.willReturn(Optional.of(owner));

			// when
			ResOwnerApprovalDtoV1 result = adminService.getOwnerApprovalDetail(ownerId);

			// then
			assertThat(result).isNotNull();
		}

		@Test
		@DisplayName("존재하지 않는 Owner 조회 시 예외 발생")
		void getOwnerApprovalDetail_NotFound_ThrowsException() {
			// given
			given(ownerRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminService.getOwnerApprovalDetail(ownerId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("approveOwner 메서드")
	class ApproveOwnerTest {

		@Test
		@DisplayName("Owner 승인 성공")
		void approveOwner_Success() {
			// given
			given(ownerRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
				.willReturn(Optional.of(owner));

			// when
			ResOwnerApprovalDtoV1 result = adminService.approveOwner(ownerId);

			// then
			assertThat(result).isNotNull();
			assertThat(owner.isApproved()).isTrue();
		}

		@Test
		@DisplayName("이미 승인된 Owner 재승인 시 예외 발생")
		void approveOwner_AlreadyApproved_ThrowsException() {
			// given
			owner.approve();
			given(ownerRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
				.willReturn(Optional.of(owner));

			// when & then
			assertThatThrownBy(() -> adminService.approveOwner(ownerId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
		}

		@Test
		@DisplayName("거절된 Owner 승인 시 예외 발생")
		void approveOwner_Rejected_ThrowsException() {
			// given
			owner.reject("테스트 거절 사유");
			given(ownerRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
				.willReturn(Optional.of(owner));

			// when & then
			assertThatThrownBy(() -> adminService.approveOwner(ownerId))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
		}
	}

	@Nested
	@DisplayName("rejectOwner 메서드")
	class RejectOwnerTest {

		@Test
		@DisplayName("Owner 거절 성공")
		void rejectOwner_Success() {
			// given
			String rejectedReason = "서류 미비";

			given(ownerRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
				.willReturn(Optional.of(owner));

			// when
			ResOwnerApprovalDtoV1 result = adminService.rejectOwner(ownerId, rejectedReason);

			// then
			assertThat(result).isNotNull();
			assertThat(owner.isRejected()).isTrue();
			assertThat(owner.getRejectedReason()).isEqualTo(rejectedReason);
		}

		@Test
		@DisplayName("이미 승인된 Owner 거절 시 예외 발생")
		void rejectOwner_AlreadyApproved_ThrowsException() {
			// given
			owner.approve();
			given(ownerRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
				.willReturn(Optional.of(owner));

			// when & then
			assertThatThrownBy(() -> adminService.rejectOwner(ownerId, "테스트 사유"))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
		}

		@Test
		@DisplayName("존재하지 않는 Owner 거절 시 예외 발생")
		void rejectOwner_NotFound_ThrowsException() {
			// given
			given(ownerRepository.findByOwnerIdAndDeletedAtIsNull(ownerId))
				.willReturn(Optional.empty());

			// when & then
			assertThatThrownBy(() -> adminService.rejectOwner(ownerId, "테스트 사유"))
				.isInstanceOf(CustomException.class)
				.hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
		}
	}
}
