package com.groom.order.infrastructure.kafka;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderOutboxRepository extends JpaRepository<OrderOutbox, UUID> {

	/**
	 * SKIP LOCKED를 사용하여 다른 인스턴스가 처리 중인 행은 건너뛰고
	 * INIT 상태의 레코드를 배치 단위로 가져옵니다.
	 * 수평 확장 시 락 경합을 제거합니다.
	 */
	@Query(value = """
			SELECT * FROM order_outbox
			WHERE status = 'INIT'
			ORDER BY created_at
			LIMIT :batchSize
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<OrderOutbox> findBatchForPublish(@Param("batchSize") int batchSize);

	/**
	 * 처리 완료된(PUBLISHED/FAILED) 오래된 레코드를 삭제하여
	 * 테이블 비대화를 방지합니다.
	 */
	@Modifying
	@Query(value = """
			DELETE FROM order_outbox
			WHERE status IN ('PUBLISHED', 'FAILED')
			  AND created_at < :cutoff
			""", nativeQuery = true)
	int deleteOldProcessedRecords(@Param("cutoff") Instant cutoff);
}
