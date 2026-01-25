package com.groom.e_commerce.user.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Arrays;
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
import com.groom.e_commerce.user.application.service.AddressServiceV1;
import com.groom.e_commerce.user.presentation.controller.AddressControllerV1;
import com.groom.e_commerce.user.presentation.dto.request.address.ReqAddressDtoV1;
import com.groom.e_commerce.user.presentation.dto.response.address.ResAddressDtoV1;

@WebMvcTest(AddressControllerV1.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AddressControllerV1 단위 테스트")
class AddressControllerV1Test {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private AddressServiceV1 addressService;

	private static final String BASE_URL = "/api/v1/users/me/addresses";
	private static final String USER_ID_HEADER = "X-User-Id";

	private UUID userId;
	private UUID addressId;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		addressId = UUID.randomUUID();
	}

	@Nested
	@DisplayName("GET /api/v1/users/me/addresses - 배송지 목록 조회")
	class GetAddressesTest {

		@Test
		@DisplayName("배송지 목록 조회 성공")
		void getAddresses_Success() throws Exception {
			// given
			List<ResAddressDtoV1> response = Arrays.asList(
				ResAddressDtoV1.builder()
					.id(UUID.randomUUID())
					.zipCode("12345")
					.address("서울시 강남구")
					.detailAddress("테스트빌딩 101호")
					.recipient("홍길동")
					.recipientPhone("010-1111-2222")
					.isDefault(true)
					.build(),
				ResAddressDtoV1.builder()
					.id(UUID.randomUUID())
					.zipCode("67890")
					.address("서울시 서초구")
					.detailAddress("다른빌딩 202호")
					.recipient("김철수")
					.recipientPhone("010-3333-4444")
					.isDefault(false)
					.build()
			);

			given(addressService.getAddresses(userId)).willReturn(response);

			// when & then
			mockMvc.perform(get(BASE_URL)
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].zipCode").value("12345"))
				.andExpect(jsonPath("$[0].isDefault").value(true))
				.andExpect(jsonPath("$[1].zipCode").value("67890"));

			then(addressService).should().getAddresses(userId);
		}

		@Test
		@DisplayName("배송지가 없으면 빈 배열 반환")
		void getAddresses_Empty() throws Exception {
			// given
			given(addressService.getAddresses(userId)).willReturn(List.of());

			// when & then
			mockMvc.perform(get(BASE_URL)
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
		}
	}

	@Nested
	@DisplayName("POST /api/v1/users/me/addresses - 배송지 등록")
	class CreateAddressTest {

		@Test
		@DisplayName("배송지 등록 성공")
		void createAddress_Success() throws Exception {
			// given
			ReqAddressDtoV1 request = new ReqAddressDtoV1();
			request.setZipCode("12345");
			request.setAddress("서울시 강남구");
			request.setDetailAddress("테스트빌딩 101호");
			request.setRecipient("홍길동");
			request.setRecipientPhone("010-1111-2222");
			request.setIsDefault(false);

			willDoNothing().given(addressService).createAddress(eq(userId), any(ReqAddressDtoV1.class));

			// when & then
			mockMvc.perform(post(BASE_URL)
					.header(USER_ID_HEADER, userId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isOk());

			then(addressService).should().createAddress(eq(userId), any(ReqAddressDtoV1.class));
		}

		@Test
		@DisplayName("기본 배송지로 등록 성공")
		void createAddress_AsDefault_Success() throws Exception {
			// given
			ReqAddressDtoV1 request = new ReqAddressDtoV1();
			request.setZipCode("12345");
			request.setAddress("서울시 강남구");
			request.setDetailAddress("테스트빌딩 101호");
			request.setRecipient("홍길동");
			request.setRecipientPhone("010-1111-2222");
			request.setIsDefault(true);

			willDoNothing().given(addressService).createAddress(eq(userId), any(ReqAddressDtoV1.class));

			// when & then
			mockMvc.perform(post(BASE_URL)
					.header(USER_ID_HEADER, userId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isOk());
		}

		@Test
		@DisplayName("존재하지 않는 사용자로 등록 시 404 에러")
		void createAddress_UserNotFound_Returns404() throws Exception {
			// given
			ReqAddressDtoV1 request = new ReqAddressDtoV1();
			request.setZipCode("12345");
			request.setAddress("서울시 강남구");
			request.setDetailAddress("테스트빌딩 101호");
			request.setRecipient("홍길동");
			request.setRecipientPhone("010-1111-2222");
			request.setIsDefault(false);

			willThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
				.given(addressService).createAddress(eq(userId), any(ReqAddressDtoV1.class));

			// when & then
			mockMvc.perform(post(BASE_URL)
					.header(USER_ID_HEADER, userId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("PUT /api/v1/users/me/addresses/{addressId} - 배송지 수정")
	class UpdateAddressTest {

		@Test
		@DisplayName("배송지 수정 성공")
		void updateAddress_Success() throws Exception {
			// given
			ReqAddressDtoV1 request = new ReqAddressDtoV1();
			request.setZipCode("99999");
			request.setAddress("부산시 해운대구");
			request.setDetailAddress("새건물 301호");
			request.setRecipient("이영희");
			request.setRecipientPhone("010-5555-6666");
			request.setIsDefault(false);

			willDoNothing().given(addressService)
				.updateAddress(eq(userId), eq(addressId), any(ReqAddressDtoV1.class));

			// when & then
			mockMvc.perform(put(BASE_URL + "/" + addressId)
					.header(USER_ID_HEADER, userId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isOk());

			then(addressService).should().updateAddress(eq(userId), eq(addressId), any(ReqAddressDtoV1.class));
		}

		@Test
		@DisplayName("존재하지 않는 배송지 수정 시 404 에러")
		void updateAddress_NotFound_Returns404() throws Exception {
			// given
			ReqAddressDtoV1 request = new ReqAddressDtoV1();
			request.setZipCode("99999");
			request.setAddress("부산시 해운대구");
			request.setDetailAddress("새건물 301호");
			request.setRecipient("이영희");
			request.setRecipientPhone("010-5555-6666");
			request.setIsDefault(false);

			willThrow(new CustomException(ErrorCode.ADDRESS_NOT_FOUND))
				.given(addressService).updateAddress(eq(userId), eq(addressId), any(ReqAddressDtoV1.class));

			// when & then
			mockMvc.perform(put(BASE_URL + "/" + addressId)
					.header(USER_ID_HEADER, userId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isNotFound());
		}

		@Test
		@DisplayName("다른 사용자의 배송지 수정 시도 시 404 에러")
		void updateAddress_OtherUserAddress_Returns404() throws Exception {
			// given
			UUID otherUserId = UUID.randomUUID();
			ReqAddressDtoV1 request = new ReqAddressDtoV1();
			request.setZipCode("99999");
			request.setAddress("부산시 해운대구");
			request.setDetailAddress("새건물 301호");
			request.setRecipient("이영희");
			request.setRecipientPhone("010-5555-6666");
			request.setIsDefault(false);

			willThrow(new CustomException(ErrorCode.ADDRESS_NOT_FOUND))
				.given(addressService).updateAddress(eq(otherUserId), eq(addressId), any(ReqAddressDtoV1.class));

			// when & then
			mockMvc.perform(put(BASE_URL + "/" + addressId)
					.header(USER_ID_HEADER, otherUserId.toString())
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(request)))
				.andDo(print())
				.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("DELETE /api/v1/users/me/addresses/{addressId} - 배송지 삭제")
	class DeleteAddressTest {

		@Test
		@DisplayName("배송지 삭제 성공")
		void deleteAddress_Success() throws Exception {
			// given
			willDoNothing().given(addressService).deleteAddress(userId, addressId);

			// when & then
			mockMvc.perform(delete(BASE_URL + "/" + addressId)
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isOk());

			then(addressService).should().deleteAddress(userId, addressId);
		}

		@Test
		@DisplayName("존재하지 않는 배송지 삭제 시 404 에러")
		void deleteAddress_NotFound_Returns404() throws Exception {
			// given
			willThrow(new CustomException(ErrorCode.ADDRESS_NOT_FOUND))
				.given(addressService).deleteAddress(userId, addressId);

			// when & then
			mockMvc.perform(delete(BASE_URL + "/" + addressId)
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isNotFound());
		}
	}

	@Nested
	@DisplayName("POST /api/v1/users/me/addresses/{addressId}/set-default - 기본 배송지 설정")
	class SetDefaultAddressTest {

		@Test
		@DisplayName("기본 배송지 설정 성공")
		void setDefaultAddress_Success() throws Exception {
			// given
			willDoNothing().given(addressService).setDefaultAddress(userId, addressId);

			// when & then
			mockMvc.perform(post(BASE_URL + "/" + addressId + "/set-default")
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isOk());

			then(addressService).should().setDefaultAddress(userId, addressId);
		}

		@Test
		@DisplayName("이미 기본 배송지인 주소 설정 시 에러")
		void setDefaultAddress_AlreadyDefault_ReturnsError() throws Exception {
			// given
			willThrow(new CustomException(ErrorCode.ALREADY_DEFAULT_ADDRESS))
				.given(addressService).setDefaultAddress(userId, addressId);

			// when & then
			mockMvc.perform(post(BASE_URL + "/" + addressId + "/set-default")
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().is4xxClientError());
		}

		@Test
		@DisplayName("존재하지 않는 배송지를 기본 배송지로 설정 시 404 에러")
		void setDefaultAddress_NotFound_Returns404() throws Exception {
			// given
			willThrow(new CustomException(ErrorCode.ADDRESS_NOT_FOUND))
				.given(addressService).setDefaultAddress(userId, addressId);

			// when & then
			mockMvc.perform(post(BASE_URL + "/" + addressId + "/set-default")
					.header(USER_ID_HEADER, userId.toString()))
				.andDo(print())
				.andExpect(status().isNotFound());
		}
	}
}
