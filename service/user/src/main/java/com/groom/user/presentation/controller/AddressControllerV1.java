package com.groom.user.presentation.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.groom.common.util.SecurityUtil;
import com.groom.user.application.service.AddressServiceV1;
import com.groom.user.presentation.dto.request.address.ReqAddressDtoV1;
import com.groom.user.presentation.dto.response.address.ResAddressDtoV1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "User", description = "배송지 API")
@RestController
@RequestMapping("/api/v2/users/me/addresses")
@RequiredArgsConstructor
public class AddressControllerV1 {

	private final AddressServiceV1 addressService;

	@Operation(summary = "배송지 목록 조회")
	@GetMapping
	public ResponseEntity<List<ResAddressDtoV1>> getAddresses() {
		return ResponseEntity.ok(addressService.getAddresses(SecurityUtil.getCurrentUserId()));
	}

	@Operation(summary = "배송지 등록")
	@PostMapping
	public ResponseEntity<Void> createAddress(
			@Valid @RequestBody ReqAddressDtoV1 request) {
		addressService.createAddress(SecurityUtil.getCurrentUserId(), request);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "배송지 수정")
	@PutMapping("/{addressId}")
	public ResponseEntity<Void> updateAddress(
			@PathVariable UUID addressId,
			@Valid @RequestBody ReqAddressDtoV1 request) {
		addressService.updateAddress(SecurityUtil.getCurrentUserId(), addressId, request);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "배송지 삭제")
	@DeleteMapping("/{addressId}")
	public ResponseEntity<Void> deleteAddress(
			@PathVariable UUID addressId) {
		addressService.deleteAddress(SecurityUtil.getCurrentUserId(), addressId);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "기본 배송지 설정")
	@PostMapping("/{addressId}/set-default")
	public ResponseEntity<Void> setDefaultAddress(
			@PathVariable UUID addressId) {
		addressService.setDefaultAddress(SecurityUtil.getCurrentUserId(), addressId);
		return ResponseEntity.ok().build();
	}
}
