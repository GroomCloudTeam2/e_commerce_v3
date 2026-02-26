package com.groom.order.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * OrderOutboxCleaner 단위 테스트
 *
 * 처리 완료된 Outbox 레코드의 주기적 삭제 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderOutboxCleaner 테스트")
class OrderOutboxCleanerTest {

    @Mock
    private OrderOutboxRepository outboxRepository;

    @InjectMocks
    private OrderOutboxCleaner orderOutboxCleaner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderOutboxCleaner, "retentionHours", 24);
    }

    @Test
    @DisplayName("보관 기간이 지난 PUBLISHED/FAILED 레코드를 삭제해야 한다")
    void clean_ShouldDelete_OldProcessedRecords() {
        // given
        given(outboxRepository.deleteOldProcessedRecords(any(Instant.class)))
                .willReturn(10);

        // when
        orderOutboxCleaner.clean();

        // then
        then(outboxRepository).should(times(1))
                .deleteOldProcessedRecords(ArgumentMatchers.argThat((Instant cutoff) -> {
                    // cutoff은 현재 시간 - 24시간 근처여야 한다
                    Instant expected = Instant.now().minusSeconds(24 * 3600L);
                    long diffSeconds = Math.abs(cutoff.getEpochSecond() - expected.getEpochSecond());
                    return diffSeconds < 5; // 5초 이내 오차 허용
                }));
    }

    @Test
    @DisplayName("삭제할 레코드가 없으면 정상적으로 완료되어야 한다")
    void clean_WhenNoRecords_ShouldCompleteNormally() {
        // given
        given(outboxRepository.deleteOldProcessedRecords(any(Instant.class)))
                .willReturn(0);

        // when
        orderOutboxCleaner.clean();

        // then
        then(outboxRepository).should(times(1))
                .deleteOldProcessedRecords(any(Instant.class));
    }

    @Test
    @DisplayName("설정된 보관 시간에 따라 cutoff 시간이 결정되어야 한다")
    void clean_ShouldUse_ConfiguredRetentionHours() {
        // given
        int customRetentionHours = 48;
        ReflectionTestUtils.setField(orderOutboxCleaner, "retentionHours", customRetentionHours);

        given(outboxRepository.deleteOldProcessedRecords(any(Instant.class)))
                .willReturn(5);

        // when
        orderOutboxCleaner.clean();

        // then
        then(outboxRepository).should(times(1))
                .deleteOldProcessedRecords(ArgumentMatchers.argThat((Instant cutoff) -> {
                    Instant expected = Instant.now().minusSeconds(48 * 3600L);
                    long diffSeconds = Math.abs(cutoff.getEpochSecond() - expected.getEpochSecond());
                    return diffSeconds < 5;
                }));
    }
}
