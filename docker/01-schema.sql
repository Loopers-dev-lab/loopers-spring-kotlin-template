-- ================================================
-- 테이블 스키마 생성 스크립트
-- MySQL 컨테이너 시작 시 자동 실행됨
-- ================================================

USE loopers;

-- Character set 설정
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;

-- ================================================
-- 1. brands 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS brands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- 2. products 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price BIGINT NOT NULL,
    stock INT NOT NULL,
    brand_id BIGINT NOT NULL,
    likes_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- 3. members 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id VARCHAR(10) NOT NULL UNIQUE COMMENT 'MemberId VO',
    email VARCHAR(255) NOT NULL COMMENT 'Email VO',
    birth_date DATE NOT NULL COMMENT 'BirthDate VO',
    gender VARCHAR(10) NOT NULL,
    point BIGINT NOT NULL DEFAULT 0 COMMENT 'Point VO',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- 4. orders 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount BIGINT NOT NULL,
    discount_amount BIGINT NOT NULL,
    final_amount BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- 5. order_items 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL COMMENT '주문 시점 상품명 스냅샷',
    quantity INT NOT NULL,
    price BIGINT NOT NULL COMMENT '주문 시점 가격 스냅샷',
    subtotal BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- 6. likes 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT uk_likes_member_product UNIQUE (member_id, product_id),
    CONSTRAINT fk_likes_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_likes_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- 7. coupons 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    type VARCHAR(20) NOT NULL,
    discount_amount BIGINT NULL,
    discount_rate INT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- 8. member_coupons 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS member_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id VARCHAR(50) NOT NULL,
    coupon_id BIGINT NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    CONSTRAINT fk_member_coupons_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================
-- 9. commerce_payments 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS commerce_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    transaction_key VARCHAR(100) NULL,
    card_type VARCHAR(20) NULL,
    card_no_masked VARCHAR(20) NULL COMMENT '마스킹된 카드 번호 (보안)',
    failure_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ================================================
-- 인덱스 생성
-- ================================================
CREATE INDEX idx_products_brand_id ON products(brand_id);
CREATE INDEX idx_products_likes_count ON products(likes_count);
CREATE INDEX idx_members_member_id ON members(member_id);
CREATE INDEX idx_orders_member_id ON orders(member_id);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);
CREATE INDEX idx_member_coupons_member_id ON member_coupons(member_id);
CREATE INDEX idx_member_coupons_coupon_id ON member_coupons(coupon_id);
CREATE INDEX idx_commerce_payments_order_id ON commerce_payments(order_id);
CREATE INDEX idx_commerce_payments_transaction_key ON commerce_payments(transaction_key);
CREATE INDEX idx_commerce_payments_status ON commerce_payments(status);

-- ================================================
-- 10. payments 테이블 (PG Simulator용)
-- ================================================
CREATE TABLE IF NOT EXISTS payments (
    transaction_key VARCHAR(255) NOT NULL PRIMARY KEY COMMENT 'PG 트랜잭션 키 (PK)',
    user_id VARCHAR(255) NOT NULL COMMENT '사용자 ID',
    order_id VARCHAR(255) NOT NULL COMMENT '주문 ID',
    card_type VARCHAR(50) NOT NULL COMMENT '카드 타입',
    card_no VARCHAR(255) NOT NULL COMMENT '카드 번호',
    amount BIGINT NOT NULL COMMENT '결제 금액',
    callback_url VARCHAR(500) NOT NULL COMMENT '콜백 URL',
    status VARCHAR(50) NOT NULL COMMENT '결제 상태',
    reason VARCHAR(500) NULL COMMENT '결제 사유',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- PG Simulator payments 테이블 인덱스
CREATE INDEX idx_pg_user_transaction ON payments(user_id, transaction_key);
CREATE INDEX idx_pg_user_order ON payments(user_id, order_id);
CREATE UNIQUE INDEX idx_pg_unique_user_order_transaction ON payments(user_id, order_id, transaction_key);

-- ================================================
-- 11. dead_letter_queue 테이블 (DLQ for Kafka)
-- ================================================
CREATE TABLE IF NOT EXISTS dead_letter_queue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'DLQ 레코드 ID',
    event_id VARCHAR(36) NOT NULL UNIQUE COMMENT '이벤트 고유 ID',
    event_type VARCHAR(50) NOT NULL COMMENT '이벤트 타입',
    aggregate_id BIGINT NOT NULL COMMENT '집합 ID',
    payload TEXT NOT NULL COMMENT '메시지 본문 (JSON)',
    error_message TEXT NOT NULL COMMENT '에러 메시지',
    stack_trace TEXT NOT NULL COMMENT '스택 트레이스',
    original_retry_count INT NOT NULL COMMENT '원본 재시도 횟수',
    processed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '처리 완료 여부',
    processed_at DATETIME(6) NULL COMMENT '처리 완료 시각',
    resolved_by VARCHAR(255) NULL COMMENT '해결자',
    resolution TEXT NULL COMMENT '해결 방법',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    deleted_at DATETIME(6) NULL COMMENT '삭제 시각'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dead Letter Queue';

-- DLQ 테이블 인덱스
CREATE INDEX idx_dlq_created_at ON dead_letter_queue(created_at);
CREATE INDEX idx_dlq_processed ON dead_letter_queue(processed, created_at);

-- ================================================
-- 12. event_handled 테이블 (이벤트 중복 처리 방지)
-- ================================================
CREATE TABLE IF NOT EXISTS event_handled (
    event_id VARCHAR(255) NOT NULL PRIMARY KEY COMMENT '이벤트 고유 ID',
    event_type VARCHAR(50) NOT NULL COMMENT '이벤트 타입',
    occurred_at DATETIME(6) NOT NULL COMMENT '이벤트 발생 시각',
    handled_at DATETIME(6) NOT NULL COMMENT '처리 시각'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='이벤트 중복 처리 방지';

-- event_handled 테이블 인덱스 (정리 작업 및 조회 성능 향상)
CREATE INDEX idx_event_handled_type_time ON event_handled(event_type, handled_at);
CREATE INDEX idx_event_handled_cleanup ON event_handled(handled_at);

-- ================================================
-- 13. event_outbox 테이블 (Transactional Outbox Pattern)
-- ================================================
CREATE TABLE IF NOT EXISTS event_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Outbox ID',
    event_id VARCHAR(36) NOT NULL UNIQUE COMMENT '이벤트 고유 ID',
    event_type VARCHAR(50) NOT NULL COMMENT '이벤트 타입',
    aggregate_type VARCHAR(20) NOT NULL COMMENT 'Aggregate 타입',
    aggregate_id BIGINT NOT NULL COMMENT 'Aggregate ID',
    payload TEXT NOT NULL COMMENT '이벤트 페이로드 (JSON)',
    occurred_at DATETIME(6) NOT NULL COMMENT '이벤트 발생 시각',
    processed BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Kafka 발행 완료 여부',
    processed_at DATETIME(6) NULL COMMENT 'Kafka 발행 완료 시각',
    kafka_partition INT NULL COMMENT 'Kafka 파티션',
    kafka_offset BIGINT NULL COMMENT 'Kafka 오프셋',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '재시도 횟수',
    last_error TEXT NULL COMMENT '마지막 에러 메시지',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    deleted_at DATETIME(6) NULL COMMENT '삭제 시각'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Transactional Outbox Pattern';

-- event_outbox 테이블 인덱스
CREATE INDEX idx_event_outbox_processed ON event_outbox(processed, created_at);
CREATE UNIQUE INDEX idx_event_outbox_event_id ON event_outbox(event_id);
CREATE INDEX idx_event_outbox_cleanup ON event_outbox(processed, processed_at);

-- ================================================
-- 14. product_metrics 테이블 (상품별 집계 데이터)
-- ================================================
CREATE TABLE IF NOT EXISTS product_metrics (
    product_id BIGINT NOT NULL PRIMARY KEY COMMENT '상품 ID',
    likes_count INT NOT NULL DEFAULT 0 COMMENT '좋아요 수',
    views_count INT NOT NULL DEFAULT 0 COMMENT '조회 수',
    orders_count INT NOT NULL DEFAULT 0 COMMENT '주문 수',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 락 버전',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    CONSTRAINT fk_product_metrics_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품별 집계 데이터';

-- ================================================
-- 15. mv_product_rank_weekly 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    year_week VARCHAR(10) NOT NULL COMMENT 'ISO Week: 2025-W52',
    score DOUBLE NOT NULL COMMENT '랭킹 점수',
    rank_position BIGINT NOT NULL COMMENT '순위 (1~100)',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    deleted_at DATETIME(6) NULL COMMENT '삭제 시각',

    UNIQUE KEY uk_product_year_week (product_id, year_week),
    INDEX idx_year_week_rank (year_week, rank_position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주간 상품 랭킹 MV';

-- ================================================
-- 16. mv_product_rank_monthly 테이블
-- ================================================
CREATE TABLE IF NOT EXISTS mv_product_rank_monthly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    period VARCHAR(7) NOT NULL COMMENT '2025-12',
    score DOUBLE NOT NULL COMMENT '랭킹 점수',
    rank_position BIGINT NOT NULL COMMENT '순위 (1~100)',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    deleted_at DATETIME(6) NULL COMMENT '삭제 시각',

    UNIQUE KEY uk_product_period (product_id, period),
    INDEX idx_period_rank (period, rank_position)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='월간 상품 랭킹 MV';

-- ================================================
-- 17. Spring Batch 메타데이터 테이블
-- ================================================

-- Sequence Tables
CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION_SEQ (
    ID BIGINT NOT NULL,
    UNIQUE_KEY CHAR(1) NOT NULL,
    constraint UNIQUE_KEY_UN unique (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_STEP_EXECUTION_SEQ (ID, UNIQUE_KEY) VALUES (0, '0') ON DUPLICATE KEY UPDATE ID=ID;

CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_SEQ (
    ID BIGINT NOT NULL,
    UNIQUE_KEY CHAR(1) NOT NULL,
    constraint UNIQUE_KEY_UN2 unique (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_JOB_EXECUTION_SEQ (ID, UNIQUE_KEY) VALUES (0, '0') ON DUPLICATE KEY UPDATE ID=ID;

CREATE TABLE IF NOT EXISTS BATCH_JOB_SEQ (
    ID BIGINT NOT NULL,
    UNIQUE_KEY CHAR(1) NOT NULL,
    constraint UNIQUE_KEY_UN3 unique (UNIQUE_KEY)
) ENGINE=InnoDB;

INSERT INTO BATCH_JOB_SEQ (ID, UNIQUE_KEY) VALUES (0, '0') ON DUPLICATE KEY UPDATE ID=ID;

-- Job Instance
CREATE TABLE IF NOT EXISTS BATCH_JOB_INSTANCE (
    JOB_INSTANCE_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION BIGINT,
    JOB_NAME VARCHAR(100) NOT NULL,
    JOB_KEY VARCHAR(32) NOT NULL,
    constraint JOB_INST_UN unique (JOB_NAME, JOB_KEY)
) ENGINE=InnoDB;

-- Job Execution
CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION BIGINT,
    JOB_INSTANCE_ID BIGINT NOT NULL,
    CREATE_TIME DATETIME(6) NOT NULL,
    START_TIME DATETIME(6) DEFAULT NULL,
    END_TIME DATETIME(6) DEFAULT NULL,
    STATUS VARCHAR(10),
    EXIT_CODE VARCHAR(2500),
    EXIT_MESSAGE VARCHAR(2500),
    LAST_UPDATED DATETIME(6),
    constraint JOB_INST_EXEC_FK foreign key (JOB_INSTANCE_ID)
        references BATCH_JOB_INSTANCE(JOB_INSTANCE_ID)
) ENGINE=InnoDB;

-- Job Execution Params
CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_PARAMS (
    JOB_EXECUTION_ID BIGINT NOT NULL,
    PARAMETER_NAME VARCHAR(100) NOT NULL,
    PARAMETER_TYPE VARCHAR(100) NOT NULL,
    PARAMETER_VALUE VARCHAR(2500),
    IDENTIFYING CHAR(1) NOT NULL,
    constraint JOB_EXEC_PARAMS_FK foreign key (JOB_EXECUTION_ID)
        references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;

-- Step Execution
CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION (
    STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    VERSION BIGINT NOT NULL,
    STEP_NAME VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID BIGINT NOT NULL,
    CREATE_TIME DATETIME(6) NOT NULL,
    START_TIME DATETIME(6) DEFAULT NULL,
    END_TIME DATETIME(6) DEFAULT NULL,
    STATUS VARCHAR(10),
    COMMIT_COUNT BIGINT,
    READ_COUNT BIGINT,
    FILTER_COUNT BIGINT,
    WRITE_COUNT BIGINT,
    READ_SKIP_COUNT BIGINT,
    WRITE_SKIP_COUNT BIGINT,
    PROCESS_SKIP_COUNT BIGINT,
    ROLLBACK_COUNT BIGINT,
    EXIT_CODE VARCHAR(2500),
    EXIT_MESSAGE VARCHAR(2500),
    LAST_UPDATED DATETIME(6),
    constraint JOB_EXEC_STEP_FK foreign key (JOB_EXECUTION_ID)
        references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;

-- Step Execution Context
CREATE TABLE IF NOT EXISTS BATCH_STEP_EXECUTION_CONTEXT (
    STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    constraint STEP_EXEC_CTX_FK foreign key (STEP_EXECUTION_ID)
        references BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
) ENGINE=InnoDB;

-- Job Execution Context
CREATE TABLE IF NOT EXISTS BATCH_JOB_EXECUTION_CONTEXT (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    constraint JOB_EXEC_CTX_FK foreign key (JOB_EXECUTION_ID)
        references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;

-- 생성 결과 확인
SELECT '✅ loopers DB 테이블 스키마 생성 완료 (Spring Batch 메타데이터 포함)' as result;
