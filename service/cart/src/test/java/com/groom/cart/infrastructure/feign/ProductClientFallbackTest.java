package com.groom.cart.infrastructure.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.groom.common.presentation.advice.CustomException;
import com.groom.common.presentation.advice.ErrorCode;
import org.junit.jupiter.api.Test;

import com.groom.cart.application.dto.StockManagement;


class ProductClientFallbackTest {

	private final ProductClientFallback fallback = new ProductClientFallback();

	@Test
	void getProductCartInfos_shouldThrowProductServiceError() {
		// given
		List<StockManagement> requests = List.of(
			StockManagement.of(
				UUID.randomUUID(),
				UUID.randomUUID(),
				1
			)
		);

		// when & then
		assertThatThrownBy(() ->
			fallback.getProductCartInfos(requests)
		)
		.isInstanceOf(CustomException.class)
		.satisfies(ex -> {
			CustomException ce = (CustomException) ex;
			assertThat(ce.getErrorCode())
				.isEqualTo(ErrorCode.PRODUCT_SERVICE_ERROR);
		});
	}
}
