package com.groom.product.product.infrastructure.config;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.groom.product.product.domain.entity.Product;
import com.groom.product.product.domain.entity.ProductVariant;
import com.groom.product.product.domain.repository.ProductRepository;
import com.groom.product.product.infrastructure.cache.StockRedisService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application startup runner to sync stock data from DB to Redis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSyncRunner implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final StockRedisService stockRedisService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting stock synchronization from DB to Redis...");

        try {
            List<Product> products = productRepository.findAll();
            int syncCount = 0;

            for (Product product : products) {
                // Skip deleted products
                if (product.isDeleted()) {
                    continue;
                }

                if (product.getHasOptions() && !product.getVariants().isEmpty()) {
                    // Sync variant stocks
                    for (ProductVariant variant : product.getVariants()) {
                        stockRedisService.syncStock(
                                product.getId(),
                                variant.getId(),
                                variant.getStockQuantity());
                        syncCount++;
                    }
                } else {
                    // Sync product stock (no options)
                    if (product.getStockQuantity() != null) {
                        stockRedisService.syncStock(
                                product.getId(),
                                null,
                                product.getStockQuantity());
                        syncCount++;
                    }
                }
            }

            log.info("Stock synchronization completed. Synced {} items from {} products", syncCount, products.size());
        } catch (Exception e) {
            log.error("Failed to sync stock data from DB to Redis", e);
        }
    }
}
