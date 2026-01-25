package com.groom.e_commerce.user.entity;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.groom.e_commerce.user.domain.entity.owner.OwnerEntity;
import com.groom.e_commerce.user.domain.entity.owner.OwnerStatus;

class OwnerEntityTest {

	private OwnerEntity owner;

	@BeforeEach
	void setUp() {
		owner = OwnerEntity.builder()
			.ownerId(UUID.randomUUID())
			.storeName("테스트 스토어")
			.businessNo("123-45-67890")
			.zipCode("12345")
			.address("서울시 강남구")
			.detailAddress("101호")
			.bank("신한은행")
			.account("110-123-456789")
			.ownerStatus(OwnerStatus.PENDING)
			.build();
	}

	@Nested
	@DisplayName("updateInfo 메서드")
	class UpdateInfoTest {

		@Test
		@DisplayName("모든 필드가 정상적으로 업데이트된다")
		void updateAllFields() {
			// when
			owner.updateInfo("새 스토어", "999-99-99999", "54321",
				"부산시 해운대구", "202호", "국민은행", "999-999-999999");

			// then
			assertThat(owner.getStoreName()).isEqualTo("새 스토어");
			assertThat(owner.getBusinessNo()).isEqualTo("999-99-99999");
			assertThat(owner.getZipCode()).isEqualTo("54321");
			assertThat(owner.getAddress()).isEqualTo("부산시 해운대구");
			assertThat(owner.getDetailAddress()).isEqualTo("202호");
			assertThat(owner.getBank()).isEqualTo("국민은행");
			assertThat(owner.getAccount()).isEqualTo("999-999-999999");
		}

		@Test
		@DisplayName("null 값은 기존 값을 유지한다")
		void nullValuesKeepOriginal() {
			// given
			String originalStoreName = owner.getStoreName();
			String originalBusinessNo = owner.getBusinessNo();
			String originalZipCode = owner.getZipCode();
			String originalAddress = owner.getAddress();
			String originalDetailAddress = owner.getDetailAddress();
			String originalBank = owner.getBank();
			String originalAccount = owner.getAccount();

			// when
			owner.updateInfo(null, null, null, null, null, null, null);

			// then
			assertThat(owner.getStoreName()).isEqualTo(originalStoreName);
			assertThat(owner.getBusinessNo()).isEqualTo(originalBusinessNo);
			assertThat(owner.getZipCode()).isEqualTo(originalZipCode);
			assertThat(owner.getAddress()).isEqualTo(originalAddress);
			assertThat(owner.getDetailAddress()).isEqualTo(originalDetailAddress);
			assertThat(owner.getBank()).isEqualTo(originalBank);
			assertThat(owner.getAccount()).isEqualTo(originalAccount);
		}

		@Test
		@DisplayName("일부 필드만 업데이트된다")
		void updatePartialFields() {
			// given
			String originalBusinessNo = owner.getBusinessNo();
			String originalBank = owner.getBank();

			// when
			owner.updateInfo("부분 업데이트 스토어", null, "99999",
				"대구시 중구", null, null, "111-222-333333");

			// then
			assertThat(owner.getStoreName()).isEqualTo("부분 업데이트 스토어");
			assertThat(owner.getBusinessNo()).isEqualTo(originalBusinessNo);
			assertThat(owner.getZipCode()).isEqualTo("99999");
			assertThat(owner.getAddress()).isEqualTo("대구시 중구");
			assertThat(owner.getDetailAddress()).isEqualTo("101호");
			assertThat(owner.getBank()).isEqualTo(originalBank);
			assertThat(owner.getAccount()).isEqualTo("111-222-333333");
		}
	}

	@Nested
	@DisplayName("approve 메서드")
	class ApproveTest {

		@Test
		@DisplayName("승인 시 상태가 APPROVED로 변경되고 승인 시간이 기록된다")
		void approveSuccess() {
			// given
			LocalDateTime beforeApprove = LocalDateTime.now();

			// when
			owner.approve();

			// then
			assertThat(owner.getOwnerStatus()).isEqualTo(OwnerStatus.APPROVED);
			assertThat(owner.getApprovedAt()).isNotNull();
			assertThat(owner.getApprovedAt()).isAfterOrEqualTo(beforeApprove);
			assertThat(owner.getRejectedReason()).isNull();
			assertThat(owner.getRejectedAt()).isNull();
		}

		@Test
		@DisplayName("거절 상태에서 승인 시 거절 정보가 초기화된다")
		void approveFromRejectedStatus() {
			// given
			owner.reject("테스트 거절 사유");

			// when
			owner.approve();

			// then
			assertThat(owner.getOwnerStatus()).isEqualTo(OwnerStatus.APPROVED);
			assertThat(owner.getRejectedReason()).isNull();
			assertThat(owner.getRejectedAt()).isNull();
		}
	}

	@Nested
	@DisplayName("reject 메서드")
	class RejectTest {

		@Test
		@DisplayName("거절 시 상태가 REJECTED로 변경되고 거절 사유와 시간이 기록된다")
		void rejectSuccess() {
			// given
			String rejectedReason = "서류 미비";
			LocalDateTime beforeReject = LocalDateTime.now();

			// when
			owner.reject(rejectedReason);

			// then
			assertThat(owner.getOwnerStatus()).isEqualTo(OwnerStatus.REJECTED);
			assertThat(owner.getRejectedReason()).isEqualTo(rejectedReason);
			assertThat(owner.getRejectedAt()).isNotNull();
			assertThat(owner.getRejectedAt()).isAfterOrEqualTo(beforeReject);
			assertThat(owner.getApprovedAt()).isNull();
		}

		@Test
		@DisplayName("승인 상태에서 거절 시 승인 정보가 초기화된다")
		void rejectFromApprovedStatus() {
			// given
			owner.approve();
			String rejectedReason = "정책 위반";

			// when
			owner.reject(rejectedReason);

			// then
			assertThat(owner.getOwnerStatus()).isEqualTo(OwnerStatus.REJECTED);
			assertThat(owner.getApprovedAt()).isNull();
			assertThat(owner.getRejectedReason()).isEqualTo(rejectedReason);
		}
	}

	@Nested
	@DisplayName("상태 확인 메서드")
	class StatusCheckTest {

		@Test
		@DisplayName("PENDING 상태일 때 isPending은 true를 반환한다")
		void isPendingTrue() {
			assertThat(owner.isPending()).isTrue();
			assertThat(owner.isApproved()).isFalse();
			assertThat(owner.isRejected()).isFalse();
		}

		@Test
		@DisplayName("APPROVED 상태일 때 isApproved는 true를 반환한다")
		void isApprovedTrue() {
			// given
			owner.approve();

			// then
			assertThat(owner.isPending()).isFalse();
			assertThat(owner.isApproved()).isTrue();
			assertThat(owner.isRejected()).isFalse();
		}

		@Test
		@DisplayName("REJECTED 상태일 때 isRejected는 true를 반환한다")
		void isRejectedTrue() {
			// given
			owner.reject("거절 사유");

			// then
			assertThat(owner.isPending()).isFalse();
			assertThat(owner.isApproved()).isFalse();
			assertThat(owner.isRejected()).isTrue();
		}
	}
}
