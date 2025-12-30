-- 랭킹 테이블 생성 스크립트
-- product_rank_daily: 일간 랭킹 영구 저장 테이블
-- mv_product_rank_weekly: 주간 랭킹 집계 테이블 (TOP 100)
-- mv_product_rank_monthly: 월간 랭킹 집계 테이블 (TOP 100)

-- 일간 랭킹 영구 저장 테이블
-- 매일 23:55에 Redis 데이터를 스냅샷하여 저장
CREATE TABLE product_rank_daily
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    ranking_date  DATE     NOT NULL COMMENT '랭킹 날짜 (해당 일자의 랭킹)',
    product_id    BIGINT   NOT NULL COMMENT '상품 ID',
    score         DOUBLE   NOT NULL COMMENT '랭킹 점수',
    `rank`        INT      NOT NULL COMMENT '순위 (1부터 시작)',
    like_count    BIGINT   NOT NULL DEFAULT 0 COMMENT '좋아요 수 (스냅샷)',
    view_count    BIGINT   NOT NULL DEFAULT 0 COMMENT '조회 수 (스냅샷)',
    sales_count   BIGINT   NOT NULL DEFAULT 0 COMMENT '판매량 (스냅샷)',
    created_at    DATETIME NOT NULL COMMENT '생성 시점',
    updated_at    DATETIME NOT NULL COMMENT '수정 시점',
    deleted_at    DATETIME          DEFAULT NULL COMMENT '삭제 시점',
    CONSTRAINT uk_product_rank_daily_date_product UNIQUE (ranking_date, product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '일간 랭킹 영구 저장 테이블';

-- 일간 랭킹 인덱스
CREATE INDEX idx_product_rank_daily_date ON product_rank_daily (ranking_date DESC);
CREATE INDEX idx_product_rank_daily_date_rank ON product_rank_daily (ranking_date, `rank`);
CREATE INDEX idx_product_rank_daily_product_id ON product_rank_daily (product_id);

-- 주간 랭킹 집계 테이블 (TOP 100)
-- 매주 일요일 01:00에 배치로 집계
CREATE TABLE mv_product_rank_weekly
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `year_week`  VARCHAR(7) NOT NULL COMMENT '연도-주차 (YYYY''W''ww, 예: 2025W01)',
    product_id   BIGINT     NOT NULL COMMENT '상품 ID',
    score        DOUBLE     NOT NULL COMMENT '집계 점수 (주간 평균)',
    `rank`       INT        NOT NULL COMMENT '주간 순위 (1부터 시작)',
    period_start DATE       NOT NULL COMMENT '집계 시작일 (주의 월요일)',
    period_end   DATE       NOT NULL COMMENT '집계 종료일 (주의 일요일)',
    created_at   DATETIME   NOT NULL COMMENT '생성 시점',
    updated_at   DATETIME   NOT NULL COMMENT '수정 시점',
    deleted_at   DATETIME            DEFAULT NULL COMMENT '삭제 시점',
    CONSTRAINT uk_product_rank_weekly_year_week_product UNIQUE (`year_week`, product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '주간 랭킹 집계 테이블 (TOP 100)';

-- 주간 랭킹 인덱스
CREATE INDEX idx_product_rank_weekly_year_week ON mv_product_rank_weekly (`year_week` DESC);
CREATE INDEX idx_product_rank_weekly_year_week_rank ON mv_product_rank_weekly (`year_week`, `rank`);
CREATE INDEX idx_product_rank_weekly_product_id ON mv_product_rank_weekly (product_id);

-- 월간 랭킹 집계 테이블 (TOP 100)
-- 매월 1일 02:00에 배치로 집계
CREATE TABLE mv_product_rank_monthly
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `year_month` VARCHAR(6) NOT NULL COMMENT '연도-월 (yyyyMM, 예: 202501)',
    product_id   BIGINT     NOT NULL COMMENT '상품 ID',
    score        DOUBLE     NOT NULL COMMENT '집계 점수 (월간 평균)',
    `rank`       INT        NOT NULL COMMENT '월간 순위 (1부터 시작)',
    period_start DATE       NOT NULL COMMENT '집계 시작일',
    period_end   DATE       NOT NULL COMMENT '집계 종료일',
    created_at   DATETIME   NOT NULL COMMENT '생성 시점',
    updated_at   DATETIME   NOT NULL COMMENT '수정 시점',
    deleted_at   DATETIME            DEFAULT NULL COMMENT '삭제 시점',
    CONSTRAINT uk_product_rank_monthly_year_month_product UNIQUE (`year_month`, product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '월간 랭킹 집계 테이블 (TOP 100)';

-- 월간 랭킹 인덱스
CREATE INDEX idx_product_rank_monthly_year_month ON mv_product_rank_monthly (`year_month` DESC);
CREATE INDEX idx_product_rank_monthly_year_month_rank ON mv_product_rank_monthly (`year_month`, `rank`);
CREATE INDEX idx_product_rank_monthly_product_id ON mv_product_rank_monthly (product_id);
