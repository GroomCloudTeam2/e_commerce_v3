package com.groom.product.product.presentation.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groom.product.product.application.service.ProductServiceV1;
import com.groom.product.product.domain.enums.ProductStatus;
import com.groom.product.product.presentation.dto.request.ReqProductSuspendDtoV1;
import com.groom.product.product.presentation.dto.response.ResProductDtoV1;
import com.groom.product.product.presentation.dto.response.ResProductListDtoV1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Product", description = "상품 API")
@RestController
@RequestMapping("/api/v2/manager/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ProductManagerControllerV1 {

	private final ProductServiceV1 productService;

	@Operation(summary = "전체 상품 조회", description = "관리자가 전체 상품 목록을 조회합니다.")
	@GetMapping
	public ResponseEntity<Page<ResProductListDtoV1>> getAllProducts(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) ProductStatus status,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		Page<ResProductListDtoV1> response = productService.getAllProductsForManager(keyword, status, pageable);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "상품 정지", description = "관리자가 상품을 정지시킵니다.")
	@PatchMapping("/{productId}/suspend")
	public ResponseEntity<ResProductDtoV1> suspendProduct(
			@PathVariable UUID productId,
			@Valid @RequestBody ReqProductSuspendDtoV1 request) {
		ResProductDtoV1 response = productService.suspendProduct(productId, request);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "상품 정지 해제", description = "관리자가 상품 정지를 해제합니다.")
	@PatchMapping("/{productId}/restore")
	public ResponseEntity<ResProductDtoV1> restoreProduct(
			@PathVariable UUID productId) {
		ResProductDtoV1 response = productService.restoreProduct(productId);
		return ResponseEntity.ok(response);
	}
}
