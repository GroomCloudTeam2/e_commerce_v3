package com.groom.product.product.presentation.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groom.product.product.application.service.ProductOptionServiceV1;
import com.groom.product.product.application.service.ProductServiceV1;
import com.groom.product.product.application.service.ProductVariantServiceV1;
import com.groom.product.product.domain.enums.ProductStatus;
import com.groom.product.product.presentation.dto.request.ReqOptionUpdateDtoV1;
import com.groom.product.product.presentation.dto.request.ReqProductCreateDtoV1;
import com.groom.product.product.presentation.dto.request.ReqProductUpdateDtoV1;
import com.groom.product.product.presentation.dto.request.ReqVariantCreateDtoV1;
import com.groom.product.product.presentation.dto.request.ReqVariantUpdateDtoV1;
import com.groom.product.product.presentation.dto.response.ResOptionDtoV1;
import com.groom.product.product.presentation.dto.response.ResProductCreateDtoV1;
import com.groom.product.product.presentation.dto.response.ResProductDtoV1;
import com.groom.product.product.presentation.dto.response.ResProductListDtoV1;
import com.groom.product.product.presentation.dto.response.ResVariantDtoV1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Product", description = "상품 API")
@RestController
@RequestMapping("/api/v2/owner/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OWNER')")
public class ProductControllerV1 {

	private final ProductServiceV1 productService;
	private final ProductOptionServiceV1 optionService;
	private final ProductVariantServiceV1 variantService;

	@Operation(summary = "상품 등록", description = "판매자가 새 상품을 등록합니다.")
	@PostMapping
	public ResponseEntity<ResProductCreateDtoV1> createProduct(
			@Valid @RequestBody ReqProductCreateDtoV1 request) {
		ResProductCreateDtoV1 response = productService.createProduct(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "내 상품 목록 조회", description = "판매자가 자신의 상품 목록을 조회합니다.")
	@GetMapping("/owner")
	public ResponseEntity<Page<ResProductListDtoV1>> getSellerProducts(
			@RequestParam(required = false) ProductStatus status,
			@RequestParam(required = false) String keyword,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		Page<ResProductListDtoV1> response = productService.getSellerProducts(status, keyword, pageable);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "상품 수정", description = "판매자가 자신의 상품을 수정합니다.")
	@PatchMapping("/{productId}")
	public ResponseEntity<ResProductDtoV1> updateProduct(
			@PathVariable UUID productId,
			@Valid @RequestBody ReqProductUpdateDtoV1 request) {
		ResProductDtoV1 response = productService.updateProduct(productId, request);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "상품 삭제", description = "판매자가 자신의 상품을 삭제합니다. (Soft Delete)")
	@DeleteMapping("/{productId}")
	public ResponseEntity<Void> deleteProduct(
			@PathVariable UUID productId) {
		productService.deleteProduct(productId);
		return ResponseEntity.noContent().build();
	}

	// ==================== 옵션 API ====================

	@Operation(summary = "옵션 전체 수정", description = "상품의 옵션을 전체 교체합니다. (기존 옵션 삭제 후 새로 생성)")
	@PutMapping("/{productId}/options")
	public ResponseEntity<List<ResOptionDtoV1>> updateOptions(
			@PathVariable UUID productId,
			@Valid @RequestBody ReqOptionUpdateDtoV1 request) {
		List<ResOptionDtoV1> response = optionService.updateOptions(productId, request);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "옵션 목록 조회", description = "상품의 옵션 목록을 조회합니다.")
	@GetMapping("/{productId}/options")
	public ResponseEntity<List<ResOptionDtoV1>> getOptions(
			@PathVariable UUID productId) {
		List<ResOptionDtoV1> response = optionService.getOptions(productId);
		return ResponseEntity.ok(response);
	}

	// ==================== SKU(Variant) API ====================

	@Operation(summary = "SKU 추가", description = "상품에 새로운 SKU(옵션 조합)를 추가합니다.")
	@PostMapping("/{productId}/variants")
	public ResponseEntity<ResVariantDtoV1> createVariant(
			@PathVariable UUID productId,
			@Valid @RequestBody ReqVariantCreateDtoV1 request) {
		ResVariantDtoV1 response = variantService.createVariant(productId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "SKU 목록 조회", description = "상품의 SKU 목록을 조회합니다.")
	@GetMapping("/{productId}/variants")
	public ResponseEntity<List<ResVariantDtoV1>> getVariants(
			@PathVariable UUID productId) {
		List<ResVariantDtoV1> response = variantService.getVariants(productId);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "SKU 수정", description = "SKU 정보를 수정합니다.")
	@PatchMapping("/{productId}/variants/{variantId}")
	public ResponseEntity<ResVariantDtoV1> updateVariant(
			@PathVariable UUID productId,
			@PathVariable UUID variantId,
			@Valid @RequestBody ReqVariantUpdateDtoV1 request) {
		ResVariantDtoV1 response = variantService.updateVariant(productId, variantId, request);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "SKU 삭제", description = "SKU를 삭제합니다. (주문이 있으면 DISCONTINUED 처리)")
	@DeleteMapping("/{productId}/variants/{variantId}")
	public ResponseEntity<Void> deleteVariant(
			@PathVariable UUID productId,
			@PathVariable UUID variantId) {
		variantService.deleteVariant(productId, variantId);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<Void> handleAccessDeniedException(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
	}
}
