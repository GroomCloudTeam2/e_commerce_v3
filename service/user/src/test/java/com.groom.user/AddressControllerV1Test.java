// package com.groom.user;
//
// import static org.mockito.ArgumentMatchers.*;
// import static org.mockito.BDDMockito.*;
// import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
// import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
// import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
// import java.util.List;
// import java.util.UUID;
//
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Nested;
// import org.junit.jupiter.api.Test;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// import org.springframework.boot.test.mock.mockito.MockBean;
// import org.springframework.http.MediaType;
// import org.springframework.security.test.context.support.WithMockUser;
// import org.springframework.test.web.servlet.MockMvc;
//
// import com.fasterxml.jackson.databind.ObjectMapper;
// import com.groom.common.infrastructure.config.security.JwtUtil;
// import com.groom.user.application.service.AddressServiceV1;
// import com.groom.user.presentation.controller.AddressControllerV1;
// import com.groom.user.presentation.dto.request.address.ReqAddressDtoV1;
// import com.groom.user.presentation.dto.response.address.ResAddressDtoV1;
//
// @WebMvcTest(AddressControllerV1.class)
// @DisplayName("AddressControllerV1 테스트")
// class AddressControllerV1Test {
//
//     @Autowired
//     private MockMvc mockMvc;
//
//     @Autowired
//     private ObjectMapper objectMapper;
//
//     // ✅ jwtAuthenticationFilter 의존성 충족용
//     @MockBean
//     private JwtUtil jwtUtil;
//
//     @MockBean
//     private AddressServiceV1 addressService;
//
//     private UUID addressId;
//     private ResAddressDtoV1 addressResponse;
//
//     @BeforeEach
//     void setUp() {
//         addressId = UUID.randomUUID();
//         addressResponse = ResAddressDtoV1.builder()
//             .id(addressId)
//             .zipCode("12345")
//             .address("서울시 강남구")
//             .detailAddress("101동 202호")
//             .recipient("홍길동")
//             .recipientPhone("010-1234-5678")
//             .isDefault(true)
//             .build();
//     }
//
//     @Nested
//     @DisplayName("GET /api/v1/users/me/addresses")
//     class GetAddressesTest {
//
//         @Test
//         @WithMockUser
//         @DisplayName("배송지 목록 조회 성공")
//         void getAddresses_Success() throws Exception {
//             given(addressService.getAddresses(any(UUID.class)))
//                 .willReturn(List.of(addressResponse));
//
//             mockMvc.perform(get("/api/v1/users/me/addresses"))
//                 .andDo(print())
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$[0].zipCode").value("12345"))
//                 .andExpect(jsonPath("$[0].recipient").value("홍길동"));
//         }
//
//         @Test
//         @WithMockUser
//         @DisplayName("빈 배송지 목록 조회")
//         void getAddresses_Empty() throws Exception {
//             given(addressService.getAddresses(any(UUID.class)))
//                 .willReturn(List.of());
//
//             mockMvc.perform(get("/api/v1/users/me/addresses"))
//                 .andExpect(status().isOk())
//                 .andExpect(jsonPath("$").isEmpty());
//         }
//
//         @Test
//         @DisplayName("인증 없이 접근 시 401")
//         void getAddresses_Unauthorized() throws Exception {
//             mockMvc.perform(get("/api/v1/users/me/addresses"))
//                 .andExpect(status().isUnauthorized());
//         }
//     }
//
//     @Nested
//     @DisplayName("POST /api/v1/users/me/addresses")
//     class CreateAddressTest {
//
//         @Test
//         @WithMockUser
//         @DisplayName("배송지 등록 성공")
//         void createAddress_Success() throws Exception {
//             ReqAddressDtoV1 request = new ReqAddressDtoV1();
//             request.setZipCode("54321");
//             request.setAddress("서울시 서초구");
//             request.setDetailAddress("201동 301호");
//             request.setRecipient("김철수");
//             request.setRecipientPhone("010-9876-5432");
//             request.setIsDefault(false);
//
//             willDoNothing().given(addressService).createAddress(any(UUID.class), any(ReqAddressDtoV1.class));
//
//             mockMvc.perform(post("/api/v1/users/me/addresses")
//                     .contentType(MediaType.APPLICATION_JSON)
//                     .content(objectMapper.writeValueAsString(request)))
//                 .andDo(print())
//                 .andExpect(status().isOk());
//         }
//
//         @Test
//         @WithMockUser
//         @DisplayName("필수 필드 누락 시 400")
//         void createAddress_MissingRequired_BadRequest() throws Exception {
//             ReqAddressDtoV1 request = new ReqAddressDtoV1();
//             request.setZipCode("12345");
//
//             mockMvc.perform(post("/api/v1/users/me/addresses")
//                     .contentType(MediaType.APPLICATION_JSON)
//                     .content(objectMapper.writeValueAsString(request)))
//                 .andExpect(status().isBadRequest());
//         }
//     }
//
//     @Nested
//     @DisplayName("PUT /api/v1/users/me/addresses/{addressId}")
//     class UpdateAddressTest {
//
//         @Test
//         @WithMockUser
//         @DisplayName("배송지 수정 성공")
//         void updateAddress_Success() throws Exception {
//             ReqAddressDtoV1 request = new ReqAddressDtoV1();
//             request.setZipCode("99999");
//             request.setAddress("부산시 해운대구");
//             request.setDetailAddress("301동 401호");
//             request.setRecipient("이영희");
//             request.setRecipientPhone("010-5555-6666");
//             request.setIsDefault(true);
//
//             willDoNothing().given(addressService).updateAddress(any(UUID.class), any(UUID.class), any(ReqAddressDtoV1.class));
//
//             mockMvc.perform(put("/api/v1/users/me/addresses/{addressId}", addressId)
//                     .contentType(MediaType.APPLICATION_JSON)
//                     .content(objectMapper.writeValueAsString(request)))
//                 .andDo(print())
//                 .andExpect(status().isOk());
//         }
//     }
//
//     @Nested
//     @DisplayName("DELETE /api/v1/users/me/addresses/{addressId}")
//     class DeleteAddressTest {
//
//         @Test
//         @WithMockUser
//         @DisplayName("배송지 삭제 성공")
//         void deleteAddress_Success() throws Exception {
//             willDoNothing().given(addressService).deleteAddress(any(UUID.class), any(UUID.class));
//
//             mockMvc.perform(delete("/api/v1/users/me/addresses/{addressId}", addressId))
//                 .andDo(print())
//                 .andExpect(status().isOk());
//         }
//     }
//
//     @Nested
//     @DisplayName("POST /api/v1/users/me/addresses/{addressId}/set-default")
//     class SetDefaultAddressTest {
//
//         @Test
//         @WithMockUser
//         @DisplayName("기본 배송지 설정 성공")
//         void setDefaultAddress_Success() throws Exception {
//             willDoNothing().given(addressService).setDefaultAddress(any(UUID.class), any(UUID.class));
//
//             mockMvc.perform(post("/api/v1/users/me/addresses/{addressId}/set-default", addressId))
//                 .andDo(print())
//                 .andExpect(status().isOk());
//         }
//     }
// }
