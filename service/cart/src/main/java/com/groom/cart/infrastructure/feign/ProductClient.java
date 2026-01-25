package com.groom.cart.infrastructure.feign;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.groom.cart.application.dto.ProductCartInfo;
import com.groom.cart.application.dto.StockManagement;
import com.groom.common.infrastructure.feign.config.FeignConfig;

/**
 * Product 서비스 API 호출용 Feign Client
 * Cart 서비스 입장에서:
 * - 상품 존재 여부
 * - 판매 가능 여부
 * - 재고 수량
 */
@FeignClient(
    name = "product-service",
    url = "${external.product-service.url}",
    fallback = ProductClientFallback.class,
    configuration = FeignConfig.class
)
public interface ProductClient {

    @PostMapping("/api/v1/internal/products/bulk-info")
    List<ProductCartInfo> getProductCartInfos(
        @RequestBody List<StockManagement> requests
    );
}
