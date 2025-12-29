-- 상품 메트릭 테이블 생성 스크립트
-- product_metrics: 상품별 집계 메트릭 (좋아요, 조회, 판매량 등)

-- 상품별 집계 메트릭 테이블
-- Consumer가 이벤트를 처리하여 실시간 집계 데이터 유지
CREATE TABLE IF NOT EXISTS product_metrics
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id         BIGINT   NOT NULL COMMENT '상품 ID',
    like_count         BIGINT   NOT NULL DEFAULT 0 COMMENT '좋아요 수',
    view_count         BIGINT   NOT NULL DEFAULT 0 COMMENT '조회 수 (상세 페이지 조회)',
    sales_count        BIGINT   NOT NULL DEFAULT 0 COMMENT '판매량 (주문 완료 기준)',
    total_sales_amount BIGINT   NOT NULL DEFAULT 0 COMMENT '총 판매 금액',
    created_at         DATETIME NOT NULL COMMENT '생성 시점',
    updated_at         DATETIME NOT NULL COMMENT '수정 시점',
    deleted_at         DATETIME          DEFAULT NULL COMMENT '삭제 시점',
    CONSTRAINT uk_product_metrics_product_id UNIQUE (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '상품별 집계 메트릭 테이블';

-- 상품 메트릭 인덱스
CREATE INDEX IF NOT EXISTS idx_product_metrics_like_count ON product_metrics (like_count DESC);
CREATE INDEX IF NOT EXISTS idx_product_metrics_view_count ON product_metrics (view_count DESC);
CREATE INDEX IF NOT EXISTS idx_product_metrics_sales_count ON product_metrics (sales_count DESC);
