-- ================================================
-- 대량 데이터 초기화 스크립트
-- MySQL 컨테이너 시작 시 자동 실행됨
-- ================================================

USE loopers;

-- Character set 설정
SET NAMES utf8mb4;
SET CHARACTER_SET_CLIENT = utf8mb4;
SET CHARACTER_SET_RESULTS = utf8mb4;

-- 재귀 깊이 제한 증가 (10만개 데이터 생성을 위해)
SET SESSION cte_max_recursion_depth = 100000;

-- ================================================
-- 1. 브랜드 100개 생성
-- ================================================
INSERT INTO brands (name, description, created_at, updated_at)
WITH RECURSIVE numbers AS (
    SELECT 1 AS seq
    UNION ALL
    SELECT seq + 1
    FROM numbers
    WHERE seq <= 100
)
SELECT
    CONCAT('Brand_', LPAD(seq, 3, '0')),
    CONCAT('브랜드 ', seq, ' 설명'),
    NOW(),
    NOW()
FROM numbers;

-- ================================================
-- 2. 상품 10만개 생성
-- ================================================
INSERT INTO products (name, description, price, stock, brand_id, likes_count, views_count, orders_count, created_at, updated_at)
WITH RECURSIVE numbers AS (
    SELECT 1 AS seq
    UNION ALL
    SELECT seq + 1
    FROM numbers
    WHERE seq <= 100000
)
SELECT
    CONCAT('Product_', LPAD(seq, 6, '0')),
    CONCAT('상품 ', seq, ' 상세 설명'),
    FLOOR(1000 + RAND() * 99000),                    -- 가격: 1,000 ~ 100,000
    FLOOR(10 + RAND() * 990),                        -- 재고: 10 ~ 1,000
    FLOOR(1 + RAND() * 100),                         -- brand_id: 1 ~ 100
    FLOOR(RAND() * RAND() * 10000),                  -- 좋아요: 0 ~ 10,000 (편향 분포)
    FLOOR(RAND() * RAND() * 50000),                  -- 조회수: 0 ~ 50,000 (편향 분포)
    FLOOR(RAND() * RAND() * 5000),                   -- 주문수: 0 ~ 5,000 (편향 분포)
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY),
    NOW()
FROM numbers;

-- ================================================
-- 3. 테스트용 회원 데이터
-- ================================================
INSERT INTO members (id, member_id, email, birth_date, gender, point, created_at, updated_at) VALUES
(1, 'testuser01', 'test01@example.com', '1990-01-01', 'MALE', 100000, NOW(), NOW()),
(2, 'testuser02', 'test02@example.com', '1995-05-15', 'FEMALE', 50000, NOW(), NOW()),
(3, 'testuser03', 'test03@example.com', '1988-12-25', 'MALE', 200000, NOW(), NOW());

-- ================================================
-- 4. 쿠폰 데이터
-- ================================================
INSERT INTO coupons (id, name, description, type, discount_amount, discount_rate, created_at, updated_at) VALUES
(1, '10% 할인 쿠폰', '전체 상품 10% 할인', 'PERCENTAGE', NULL, 10, NOW(), NOW()),
(2, '5000원 할인 쿠폰', '5000원 즉시 할인', 'FIXED', 5000, NULL, NOW(), NOW()),
(3, '20% 할인 쿠폰', '전체 상품 20% 할인', 'PERCENTAGE', NULL, 20, NOW(), NOW());

-- ================================================
-- 5. 회원 쿠폰 (testuser01에게 쿠폰 지급)
-- ================================================
INSERT INTO member_coupons (id, member_id, coupon_id, used_at, created_at, updated_at) VALUES
(1, 'testuser01', 1, NULL, NOW(), NOW()),
(2, 'testuser01', 2, NULL, NOW(), NOW()),
(3, 'testuser02', 1, NULL, NOW(), NOW());

-- 생성 결과 확인
SELECT CONCAT('✅ 브랜드 ', COUNT(*), '개 생성 완료') as result FROM brands;
SELECT CONCAT('✅ 상품 ', COUNT(*), '개 생성 완료') as result FROM products;
SELECT CONCAT('✅ 회원 ', COUNT(*), '개 생성 완료') as result FROM members;
SELECT CONCAT('✅ 쿠폰 ', COUNT(*), '개 생성 완료') as result FROM coupons;
SELECT CONCAT('✅ 회원 쿠폰 ', COUNT(*), '개 생성 완료') as result FROM member_coupons;
