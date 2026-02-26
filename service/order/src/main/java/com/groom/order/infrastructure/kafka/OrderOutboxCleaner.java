package com.groom.order.infrastructure.kafka;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 처리 완료된 Outbox 레코드를 주기적으로 삭제하여
 * 테이블 비대화를 방지합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxCleaner {

    private final OrderOutboxRepository outboxRepository;

    @Value("${outbox.cleaner.retention-hours:24}")
    private int retentionHours;

    @Scheduled(fixedDelayString = "${outbox.cleaner.delay-ms:60000}")
    @Transactional
    public void clean() {
        Instant cutoff = Instant.now().minusSeconds(retentionHours * 3600L);
        int deleted = outboxRepository.deleteOldProcessedRecords(cutoff);
        if (deleted > 0) {
            log.info("Outbox cleaner: deleted {} old records (before {})", deleted, cutoff);
        }
    }
}
