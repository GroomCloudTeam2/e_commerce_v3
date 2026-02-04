package com.groom.product.review.infrastructure.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class ReviewRedisRepositoryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks
    private ReviewRedisRepository repository;

    @Test
    @DisplayName("리뷰 저장 시 데이터와 ZSET 인덱스가 함께 저장된다")
    void save_stores_value_and_zset_index() {
        // given
        UUID productId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        ReviewReadModel review = ReviewReadModel.builder()
            .reviewId(reviewId)
            .productId(productId)
            .userId(UUID.randomUUID())
            .rating(5)
            .content("좋아요")
            .createdAt(LocalDateTime.now())
            .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        // when
        repository.save(review);

        // then
        String indexKey = ReviewRedisKey.productReviewIndex(productId);
        String dataKey = ReviewRedisKey.reviewData(reviewId);
        long score = review.getCreatedAt().toEpochSecond(ZoneOffset.UTC);

        verify(valueOperations).set(dataKey, review);
        verify(zSetOperations)
            .add(indexKey, reviewId.toString(), score);
    }

    @Test
    @DisplayName("Redis에 리뷰가 없으면 빈 리스트를 반환한다")
    void findByProductId_returns_empty_when_no_index() {
        UUID productId = UUID.randomUUID();

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
            .thenReturn(Set.of());

        List<ReviewReadModel> result =
            repository.findByProductId(productId, 0, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Redis 인덱스와 데이터가 존재하면 리뷰 목록을 반환한다")
    void findByProductId_returns_reviews() {
        UUID productId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        ReviewReadModel review = ReviewReadModel.builder()
            .reviewId(reviewId)
            .productId(productId)
            .userId(UUID.randomUUID())
            .rating(4)
            .content("괜찮아요")
            .createdAt(LocalDateTime.now())
            .build();

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
            .thenReturn(Set.of(reviewId.toString()));

        when(valueOperations.get(anyString())).thenReturn(review);

        List<ReviewReadModel> result =
            repository.findByProductId(productId, 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("괜찮아요");
    }

    @Test
    @DisplayName("리뷰 삭제 시 ZSET 인덱스와 데이터가 함께 삭제된다")
    void delete_removes_index_and_data() {
        UUID productId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();

        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        repository.delete(productId, reviewId);

        String indexKey = ReviewRedisKey.productReviewIndex(productId);
        String dataKey = ReviewRedisKey.reviewData(reviewId);

        verify(zSetOperations)
            .remove(indexKey, reviewId.toString());
        verify(redisTemplate).delete(dataKey);
    }
}
