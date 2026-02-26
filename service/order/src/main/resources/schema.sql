-- Partial Index: INIT 상태의 레코드만 인덱스에 포함하여 DB 검색 부하를 최소화
-- 전체 테이블 스캔 대신 미발행 레코드만 효율적으로 조회
CREATE INDEX IF NOT EXISTS idx_outbox_init_created_at ON order_outbox (created_at)
WHERE
    status = 'INIT';
    