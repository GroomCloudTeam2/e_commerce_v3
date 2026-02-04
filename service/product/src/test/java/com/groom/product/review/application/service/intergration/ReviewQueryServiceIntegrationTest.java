//package com.groom.product.review.application.service.intergration;
//
//import static org.assertj.core.api.Assertions.*;
//
//import java.util.UUID;
//
//import com.groom.product.review.application.service.ReviewQueryService;
//import com.groom.product.review.domain.entity.ReviewEntity;
//import com.groom.product.review.domain.repository.ReviewRepository;
//import com.groom.product.review.infrastructure.redis.ReviewRedisRepository;
//import org.junit.jupiter.api.Tag;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//
//@SpringBootTest
//@Tag("integration")
//class ReviewQueryServiceIntegrationTest {
//
//    @Autowired
//    private ReviewQueryService service;
//
//    @Autowired
//    private ReviewRepository reviewRepository;
//
//    @Autowired
//    private ReviewRedisRepository reviewRedisRepository;
//
//    @Test
//    void 두번째_조회부터는_Redis_HIT이_발생한다() {
//        UUID productId = UUID.randomUUID();
//
//        reviewRepository.save(
//            ReviewEntity.builder()
//                .productId(productId)
//                .rating(5)
//                .content("색감이 이쁘다")
//                .build()
//        );
//
//        // 첫 조회 (DB)
//        service.getProductReviews(productId, 0, 10);
//
//        // 두 번째 조회 (Redis)
//        service.getProductReviews(productId, 0, 10);
//
//        // Redis에 데이터가 저장되었는지 확인
//        assertThat(
//            reviewRedisRepository.findByProductId(productId, 0, 10)
//        ).isNotEmpty();
//    }
//}
