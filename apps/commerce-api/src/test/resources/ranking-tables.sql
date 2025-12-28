-- 주간 랭킹 테이블
DROP TABLE IF EXISTS mv_product_rank_weekly;
CREATE TABLE mv_product_rank_weekly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    `year_week` VARCHAR(7) NOT NULL,
    product_id BIGINT NOT NULL,
    score DOUBLE NOT NULL,
    `rank` INT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX uk_product_rank_weekly_year_week_product ON mv_product_rank_weekly (`year_week`, product_id);
CREATE INDEX idx_product_rank_weekly_year_week ON mv_product_rank_weekly (`year_week` DESC);
CREATE INDEX idx_product_rank_weekly_year_week_rank ON mv_product_rank_weekly (`year_week`, `rank`);

-- 월간 랭킹 테이블
DROP TABLE IF EXISTS mv_product_rank_monthly;
CREATE TABLE mv_product_rank_monthly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    `year_month` VARCHAR(6) NOT NULL,
    product_id BIGINT NOT NULL,
    score DOUBLE NOT NULL,
    `rank` INT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    created_at TIMESTAMP NULL,
    updated_at TIMESTAMP NULL,
    deleted_at TIMESTAMP NULL
);

CREATE UNIQUE INDEX uk_product_rank_monthly_year_month_product ON mv_product_rank_monthly (`year_month`, product_id);
CREATE INDEX idx_product_rank_monthly_year_month ON mv_product_rank_monthly (`year_month` DESC);
CREATE INDEX idx_product_rank_monthly_year_month_rank ON mv_product_rank_monthly (`year_month`, `rank`);
