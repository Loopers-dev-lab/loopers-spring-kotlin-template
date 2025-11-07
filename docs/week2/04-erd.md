# ERD (Entity Relationship Diagram)

본 문서는 감성 이커머스의 데이터베이스 구조를 정의합니다.

---

## 1. ERD 다이어그램

![img_4.png](https://i.imgur.com/TTCD1uh.png)

**관계 표기:**

- **실선 (||--|{)**: FK 제약이 있는 관계 (Order ↔ OrderItem)
- **점선 (||..o{)**: ID로만 참조하는 관계 (FK 제약 없음)

---

## 2. 테이블 상세 정의

### 2.1 Products 도메인

#### brands (브랜드)

| 컬럼명        | 타입           | 제약                 | 설명                  |
|------------|--------------|--------------------|---------------------|
| brand_id   | BIGINT       | PK, AUTO_INCREMENT | 브랜드 ID              |
| name       | VARCHAR(100) | NOT NULL, UNIQUE   | 브랜드 명               |
| created_at | TIMESTAMP    | NOT NULL           | 생성 시각               |
| updated_at | TIMESTAMP    | NOT NULL           | 수정 시각               |
| deleted_at | TIMESTAMP    | NULL               | 삭제 시각 (Soft Delete) |

**인덱스:**

- PK: brand_id
- UK: name

---

#### products (상품)

| 컬럼명        | 타입            | 제약                 | 설명                                         |
|------------|---------------|--------------------|--------------------------------------------|
| product_id | BIGINT        | PK, AUTO_INCREMENT | 상품 ID                                      |
| brand_id   | BIGINT        | NOT NULL           | 브랜드 ID (참조)                                |
| name       | VARCHAR(200)  | NOT NULL           | 상품명                                        |
| price      | DECIMAL(15,2) | NOT NULL           | 가격                                         |
| status     | VARCHAR(20)   | NOT NULL           | 상태 (AVAILABLE, OUT_OF_STOCK, DISCONTINUED) |
| created_at | TIMESTAMP     | NOT NULL           | 생성 시각                                      |
| updated_at | TIMESTAMP     | NOT NULL           | 수정 시각                                      |
| deleted_at | TIMESTAMP     | NULL               | 삭제 시각 (Soft Delete)                        |

**인덱스:**

- PK: product_id
- IDX: brand_id (브랜드별 상품 조회용)
- IDX: created_at (정렬용)

---

#### stocks (재고)

| 컬럼명        | 타입        | 제약                 | 설명                  |
|------------|-----------|--------------------|---------------------|
| stock_id   | BIGINT    | PK, AUTO_INCREMENT | 재고 ID               |
| product_id | BIGINT    | NOT NULL, UNIQUE   | 상품 ID (참조)          |
| quantity   | INT       | NOT NULL           | 재고 수량               |
| created_at | TIMESTAMP | NOT NULL           | 생성 시각               |
| updated_at | TIMESTAMP | NOT NULL           | 수정 시각               |
| deleted_at | TIMESTAMP | NULL               | 삭제 시각 (Soft Delete) |

**인덱스:**

- PK: stock_id
- UK: product_id

**제약:**

- quantity >= 0 (CHECK)

---

### 2.2 Likes 도메인

#### product_likes (상품 좋아요)

| 컬럼명             | 타입        | 제약                 | 설명                  |
|-----------------|-----------|--------------------|---------------------|
| product_like_id | BIGINT    | PK, AUTO_INCREMENT | 좋아요 ID              |
| user_id         | BIGINT    | NOT NULL           | 사용자 ID (참조)         |
| product_id      | BIGINT    | NOT NULL           | 상품 ID (참조)          |
| created_at      | TIMESTAMP | NOT NULL           | 생성 시각               |
| updated_at      | TIMESTAMP | NOT NULL           | 수정 시각               |
| deleted_at      | TIMESTAMP | NULL               | 삭제 시각 (Soft Delete) |

**인덱스:**

- PK: product_like_id
- UK: (user_id, product_id)
- IDX: user_id (내가 좋아요한 목록 조회용)
- IDX: product_id (상품별 좋아요 조회용)

---

#### product_like_counts (상품 좋아요 수)

| 컬럼명                   | 타입        | 제약                 | 설명                  |
|-----------------------|-----------|--------------------|---------------------|
| product_like_count_id | BIGINT    | PK, AUTO_INCREMENT | 좋아요 수 ID            |
| product_id            | BIGINT    | NOT NULL, UNIQUE   | 상품 ID (참조)          |
| count                 | INT       | NOT NULL           | 좋아요 수               |
| created_at            | TIMESTAMP | NOT NULL           | 생성 시각               |
| updated_at            | TIMESTAMP | NOT NULL           | 수정 시각               |
| deleted_at            | TIMESTAMP | NULL               | 삭제 시각 (Soft Delete) |

**인덱스:**

- PK: product_like_count_id
- UK: product_id

**제약:**

- count >= 0 (CHECK)

**특이사항:**

- 좋아요 취소에 대해 하드 딜리트로 처리

---

### 2.3 Orders 도메인

#### orders (주문)

| 컬럼명          | 타입            | 제약                 | 설명                   |
|--------------|---------------|--------------------|----------------------|
| order_id     | BIGINT        | PK, AUTO_INCREMENT | 주문 ID                |
| user_id      | BIGINT        | NOT NULL           | 사용자 ID (참조)          |
| total_amount | DECIMAL(15,2) | NOT NULL           | 총 주문 금액              |
| status       | VARCHAR(20)   | NOT NULL           | 주문 상태 (PLACED, PAID) |
| created_at   | TIMESTAMP     | NOT NULL           | 생성 시각                |
| updated_at   | TIMESTAMP     | NOT NULL           | 수정 시각                |
| deleted_at   | TIMESTAMP     | NULL               | 삭제 시각 (Soft Delete)  |

**인덱스:**

- PK: order_id
- IDX: user_id (사용자별 주문 목록 조회용)
- IDX: created_at (최신 주문 조회용)

---

#### order_items (주문 항목)

| 컬럼명           | 타입            | 제약                 | 설명                         |
|---------------|---------------|--------------------|----------------------------|
| order_item_id | BIGINT        | PK, AUTO_INCREMENT | 주문 항목 ID                   |
| order_id      | BIGINT        | NOT NULL, FK       | 주문 ID                      |
| product_id    | BIGINT        | NOT NULL           | 상품 ID (참조)                 |
| product_name  | VARCHAR(200)  | NOT NULL           | 상품명 (스냅샷)                  |
| unit_price    | DECIMAL(15,2) | NOT NULL           | 단가 (스냅샷)                   |
| quantity      | INT           | NOT NULL           | 수량                         |
| subtotal      | DECIMAL(15,2) | NOT NULL           | 소계 (unit_price × quantity) |
| created_at    | TIMESTAMP     | NOT NULL           | 생성 시각                      |
| updated_at    | TIMESTAMP     | NOT NULL           | 수정 시각                      |
| deleted_at    | TIMESTAMP     | NULL               | 삭제 시각 (Soft Delete)        |

**인덱스:**

- PK: order_item_id
- FK: order_id → orders(order_id) ON DELETE CASCADE
- IDX: product_id (상품별 주문 이력 조회용)

---

#### payments (결제)

| 컬럼명          | 타입            | 제약                 | 설명                  |
|--------------|---------------|--------------------|---------------------|
| payment_id   | BIGINT        | PK, AUTO_INCREMENT | 결제 ID               |
| order_id     | BIGINT        | NOT NULL, UNIQUE   | 주문 ID (참조)          |
| user_id      | BIGINT        | NOT NULL           | 사용자 ID (참조)         |
| total_amount | DECIMAL(15,2) | NOT NULL           | 총 주문 금액             |
| used_point   | DECIMAL(15,2) | NOT NULL           | 사용 포인트              |
| paid_amount  | DECIMAL(15,2) | NOT NULL           | 실제 결제 금액            |
| status       | VARCHAR(20)   | NOT NULL           | 결제 상태 (READY, PAID) |
| created_at   | TIMESTAMP     | NOT NULL           | 생성 시각               |
| updated_at   | TIMESTAMP     | NOT NULL           | 수정 시각               |
| deleted_at   | TIMESTAMP     | NULL               | 삭제 시각 (Soft Delete) |

**인덱스:**

- PK: payment_id
- UK: order_id
- IDX: user_id (사용자별 결제 이력 조회용)

---

### 2.4 Points 도메인

#### point_accounts (포인트 계좌)

| 컬럼명              | 타입            | 제약                 | 설명                  |
|------------------|---------------|--------------------|---------------------|
| point_account_id | BIGINT        | PK, AUTO_INCREMENT | 포인트 계좌 ID           |
| user_id          | BIGINT        | NOT NULL, UNIQUE   | 사용자 ID (참조)         |
| balance          | DECIMAL(15,2) | NOT NULL           | 포인트 잔액              |
| created_at       | TIMESTAMP     | NOT NULL           | 생성 시각               |
| updated_at       | TIMESTAMP     | NOT NULL           | 수정 시각               |
| deleted_at       | TIMESTAMP     | NULL               | 삭제 시각 (Soft Delete) |

**인덱스:**

- PK: point_account_id
- UK: user_id

**제약:**

- balance >= 0 (CHECK)

---

#### point_histories (포인트 이력)

| 컬럼명           | 타입            | 제약                 | 설명                       |
|---------------|---------------|--------------------|--------------------------|
| history_id    | BIGINT        | PK, AUTO_INCREMENT | 포인트 이력 ID                |
| user_id       | BIGINT        | NOT NULL           | 사용자 ID (참조)              |
| reference_id  | VARCHAR(100)  | NULL               | 참조 ID (충전ID, 결제ID 등)     |
| type          | VARCHAR(20)   | NOT NULL           | 포인트 타입 (CHARGE, PAYMENT) |
| amount        | DECIMAL(15,2) | NOT NULL           | 거래 금액                    |
| balance_after | DECIMAL(15,2) | NOT NULL           | 거래 후 잔액                  |
| created_at    | TIMESTAMP     | NOT NULL           | 생성 시각                    |
| updated_at    | TIMESTAMP     | NOT NULL           | 수정 시각                    |
| deleted_at    | TIMESTAMP     | NULL               | 삭제 시각 (Soft Delete)      |

**인덱스:**

- PK: history_id
- IDX: user_id,created_at  (사용자별 이력 조회용)

---

## 3. 설계 고려사항

### 3.1 동시성 제어

- **stocks 테이블**: 재고 차감 시 비관적 락(`SELECT ... FOR UPDATE`) 사용

### 3.2 데이터 정합성

- **order_items**: 주문 시점의 상품 정보를 스냅샷으로 저장 (가격 변동 영향 없음)
- **payments**: total_amount = used_point + paid_amount 제약으로 정합성 보장

### 3.3 FK 제약 전략

- **Order-OrderItem**: 같은 애그리게이트이므로 FK 유지 (ON DELETE CASCADE)
- **나머지**: 애그리게이트 간 느슨한 결합을 위해 FK 제거, 애플리케이션 레벨에서 참조 무결성 관리

### 3.4 Soft Delete

- 모든 테이블에 deleted_at 컬럼 적용
- 실제 데이터 삭제 대신 deleted_at에 타임스탬프 기록
- 조회 시 deleted_at IS NULL 조건 추가

### 3.5 성능 최적화

- **product_like_counts**: 좋아요 수 집계 테이블로 조회 성능 향상
- **인덱스**: 자주 조회되는 컬럼에 인덱스 설정 (created_at, user_id, status 등)

### 3.6 확장성

- 모든 ID는 BIGINT 사용으로 대용량 데이터 대비
- DECIMAL(15,2)로 최대 9,999,999,999,999.99까지 금액 처리 가능
