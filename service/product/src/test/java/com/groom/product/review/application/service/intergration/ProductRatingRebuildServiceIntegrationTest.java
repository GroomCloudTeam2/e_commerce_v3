//package com.groom.product.review.application.service.intergration;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//import java.util.UUID;
//
//import com.groom.product.review.application.service.ProductRatingRebuildService;
//import com.groom.product.review.domain.entity.ProductRatingEntity;
//import com.groom.product.review.domain.entity.ReviewCategory;
//import com.groom.product.review.domain.entity.ReviewEntity;
//import com.groom.product.review.domain.repository.ProductRatingRepository;
//import com.groom.product.review.domain.repository.ReviewRepository;
//import org.junit.jupiter.api.Tag;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.springframework.transaction.annotation.Transactional;
//import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//@SpringBootTest
//@Testcontainers
//@Tag("integration")
//class ProductRatingRebuildServiceIntegrationTest {
//
//    @Container
//    static PostgreSQLContainer<?> postgres =
//            new PostgreSQLContainer<>("postgres:15-alpine")
//                    .withDatabaseName("testdb")
//                    .withUsername("test")
//                    .withPassword("test");
//    @DynamicPropertySource
//    static void registerPgProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//        registry.add("spring.datasource.driver-class-name",
//                postgres::getDriverClassName);
//    }
//
//    @Autowired
//    private ProductRatingRebuildService rebuildService;
//
//    @Autowired
//    private ReviewRepository reviewRepository;
//
//    @Autowired
//    private ProductRatingRepository productRatingRepository;
//
//    @Test
//    @Transactional
//    void 리뷰_기반으로_상품평점이_정상적으로_재계산된다() {
//        // given
//        UUID productId = UUID.randomUUID();
//
//        reviewRepository.save(
//                ReviewEntity.builder()
//                        .orderId(UUID.randomUUID())
//                        .productId(productId)
//                        .userId(UUID.randomUUID())
//                        .rating(4)
//                        .content("괜찮아요")
//                        .category(ReviewCategory.PERFORMANCE)
//                        .build()
//        );
//
//        reviewRepository.save(
//                ReviewEntity.builder()
//                        .orderId(UUID.randomUUID())
//                        .productId(productId)
//                        .userId(UUID.randomUUID())
//                        .rating(5)
//                        .content("아주 좋아요")
//                        .category(ReviewCategory.QUALITY)
//                        .build()
//        );
//
//        // when
//        rebuildService.rebuild(productId);
//
//        // then
//        ProductRatingEntity rating =
//                productRatingRepository.findByProductId(productId).orElseThrow();
//
//        assertThat(rating.getReviewCount()).isEqualTo(2);
//        assertThat(rating.getAvgRating()).isEqualTo(4.5);
//    }
//}
