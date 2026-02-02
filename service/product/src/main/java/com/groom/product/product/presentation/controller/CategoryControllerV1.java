package com.groom.product.product.presentation.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.groom.product.product.application.service.CategoryServiceV1;
import com.groom.product.product.presentation.dto.response.ResCategoryDtoV1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Category", description = "카테고리 API")
@RestController
@RequestMapping("/api/v2/categories")
@RequiredArgsConstructor
public class CategoryControllerV1 {

	private final CategoryServiceV1 categoryService;

	@Operation(summary = "카테고리 목록 조회")
	@GetMapping
	public ResponseEntity<List<ResCategoryDtoV1>> getCategories(
			@RequestParam(required = false) UUID parentId) {
		List<ResCategoryDtoV1> categories;
		if (parentId == null) {
			categories = categoryService.getAllCategories();
		} else {
			categories = categoryService.getChildCategories(parentId);
		}
		return ResponseEntity.ok(categories);
	}

	@Operation(summary = "카테고리 상세 조회")
	@GetMapping("/{categoryId}")
	public ResponseEntity<ResCategoryDtoV1> getCategory(
			@PathVariable UUID categoryId) {
		ResCategoryDtoV1 category = categoryService.getCategory(categoryId);
		return ResponseEntity.ok(category);
	}
}
