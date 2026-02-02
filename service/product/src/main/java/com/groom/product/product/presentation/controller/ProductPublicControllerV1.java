package com.groom.product.product.presentation.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groom.common.presentation.advice.CustomException;
import com.groom.common.presentation.advice.ErrorCode;
import com.groom.product.product.application.service.ProductReadService;
import com.groom.product.product.application.service.ProductServiceV1;
import com.groom.product.product.application.service.ProductWarmUpLoader;
import com.groom.product.product.domain.enums.ProductSortType;
import com.groom.product.product.presentation.dto.response.ResProductDetailDtoV1;
import com.groom.product.product.presentation.dto.response.ResProductSearchDtoV1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Product", description = "상품 API")
@RestController
@RequestMapping("/api/v2/products")
@RequiredArgsConstructor
public class ProductPublicControllerV1 {

	private final ProductReadService productReadService;
	private final ProductServiceV1 productService;
	private final ProductWarmUpLoader warmUpLoader;

	@Operation(summary = "상품 캐시 웜업 (테스트용)", description = "최신 상품 10만 개를 Redis 캐시에 적재합니다.")
	@PostMapping("/warm-up")
	public ResponseEntity<String> warmUp() {
		new Thread(warmUpLoader::loadTopProducts).start();
		return ResponseEntity.ok("Warm-up started in background (Limit: 100,000)");
	}

	@Operation(summary = "상품 목록 조회", description = "구매자가 상품 목록을 조회합니다. (검색, 필터, 정렬 지원)")
	@GetMapping
	public ResponseEntity<Page<ResProductSearchDtoV1>> searchProducts(
			@Parameter(description = "카테고리 ID") @RequestParam(required = false) UUID categoryId,
			@Parameter(description = "검색어 (상품명)") @RequestParam(required = false) String keyword,
			@Parameter(description = "최소 가격") @RequestParam(required = false) Long minPrice,
			@Parameter(description = "최대 가격") @RequestParam(required = false) Long maxPrice,
			@Parameter(description = "정렬 (price_asc, price_desc, newest, rating)") @RequestParam(required = false, defaultValue = "newest") String sort,
			@Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(required = false, defaultValue = "1") Integer page,
			@Parameter(description = "페이지 크기") @RequestParam(required = false, defaultValue = "20") Integer size) {
		ProductSortType sortType = ProductSortType.fromValue(sort);
		// 명세에서는 page가 1부터 시작하므로, 0-based로 변환
		Pageable pageable = PageRequest.of(Math.max(0, page - 1), size);

		Page<ResProductSearchDtoV1> response;

		// 단순 카테고리 조회 (필터 없음, 최신순) → Redis 캐시 사용
		boolean isSimpleQuery = (keyword == null || keyword.isBlank())
				&& minPrice == null
				&& maxPrice == null
				&& (sortType == null || sortType == ProductSortType.NEWEST);

		if (isSimpleQuery) {
			response = productReadService.getProductList(categoryId, pageable);
		} else {
			// 복잡한 검색 (필터/정렬) → DB 직접 조회
			response = productService.searchProducts(
					categoryId, keyword, minPrice, maxPrice, sortType, pageable);
		}

		return ResponseEntity.ok(response);
	}

	@Operation(summary = "상품 상세 조회", description = "구매자가 상품 상세 정보를 조회합니다.")
	@GetMapping("/{productId}")
	public ResponseEntity<ResProductDetailDtoV1> getProductDetail(
			@Parameter(description = "상품 ID") @PathVariable UUID productId) {
		// Redis 캐시 조회 → 캐시 미스 시 DB Fallback (내부 처리)
		ResProductDetailDtoV1 response = productReadService.getProductDetail(productId);

		if (response == null) {
			throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND);
		}

		return ResponseEntity.ok(response);
	}
}
