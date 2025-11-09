# ERD

```mermaid
erDiagram

%% 사용자
    USER {
        bigint id PK "사용자 기본키"
        varchar email "사용자 이메일"
        date birth "생년월일"
        varchar gender "성별"
        timestamp created_at "생성일시"
        timestamp updated_at "수정일시"
        timestamp deleted_at "삭제일시"
    }

%% 브랜드
    BRAND {
        bigint id PK "브랜드 기본키"
        varchar name "브랜드명"
        timestamp created_at "생성일시"
        timestamp updated_at "수정일시"
        timestamp deleted_at "삭제일시"
    }

%% 상품
    PRODUCT {
        bigint id PK "상품 기본키"
        varchar name "상품명"
        bigint price "상품 가격"
        bigint ref_brand_id FK "브랜드 ID (BRAND 참조)"
        timestamp created_at "생성일시"
        timestamp updated_at "수정일시"
        timestamp deleted_at "삭제일시"
    }

%% 재고
    STOCK {
        bigint id PK "재고 기본키"
        bigint ref_product_id FK "상품 ID (PRODUCT 참조)"
        bigint quantity "현재 재고 수량"
        timestamp created_at "생성일시"
        timestamp updated_at "수정일시"
        timestamp deleted_at "삭제일시"
    }

%% 주문
    ORDER {
        bigint id PK "주문 기본키"
        varchar status "주문 상태 (PENDING, COMPLETED, CANCELLED)"
        bigint total_amount "총 주문 금액"
        bigint ref_user_id FK "주문자 ID (USER 참조)"
        timestamp created_at "생성일시"
        timestamp updated_at "수정일시"
        timestamp deleted_at "삭제일시"
    }

%% 주문 상세
    ORDER_DETAIL {
        bigint id PK "주문 상세 기본키"
        varchar brand_name "주문 당시 브랜드명 (스냅샷)"
        varchar product_name "주문 당시 상품명 (스냅샷)"
        bigint quantity "주문 수량"
        bigint price "상품 단가"
        bigint ref_brand_id FK "브랜드 ID (BRAND 참조)"
        bigint ref_product_id FK "상품 ID (PRODUCT 참조)"
        bigint ref_order_id FK "주문 ID (ORDER 참조)"
        timestamp created_at "생성일시"
        timestamp updated_at "수정일시"
        timestamp deleted_at "삭제일시"
    }

%% 상품 좋아요
    PRODUCT_LIKE {
        bigint id PK "좋아요 기본키"
        bigint ref_product_id FK "상품 ID (PRODUCT 참조)"
        bigint ref_user_id FK "사용자 ID (USER 참조)"
        timestamp created_at "생성일시"
    }

%% 포인트
    POINT {
        bigint id PK "포인트 기본키"
        bigint amount "현재 보유 포인트 잔액"
        bigint ref_user_id FK "사용자 ID (USER 참조)"
        timestamp created_at "생성일시"
        timestamp updated_at "수정일시"
        timestamp deleted_at "삭제일시"
    }

%% 관계
    USER ||--o{ ORDER: "주문"
    USER ||--|| POINT: "보유"
    BRAND ||--o{ PRODUCT: "소속"
    PRODUCT ||--|| STOCK: "재고"
    PRODUCT ||--o{ PRODUCT_LIKE: "좋아요"
    PRODUCT ||--o{ ORDER_DETAIL: "포함됨"
    ORDER ||--|{ ORDER_DETAIL: "주문 상품"
```

# 🗂️ 테이블별 인덱스 & 제약조건

- 물리적 외래키는 생성하지 않고, 애플리케이션에서 검증한다.
- `deleted_at` null 여부로 삭제 여부를 판단한다.

## USER

**제약조건**

- PRIMARY KEY: `id`
- UNIQUE: `email` (이메일 중복 방지)

**인덱스**

- `idx_user_email` (`email`)

---

## BRAND

**제약조건**

- PRIMARY KEY: `id`
- UNIQUE: `name` (브랜드명 중복 방지)

**인덱스**

- `idx_brand_name` (`name`)

---

## PRODUCT

**제약조건**

- PRIMARY KEY: `id`
- FOREIGN KEY: `brand_id` → `BRAND(id)`

**인덱스**

- `idx_product_brand_id` (`brand_id`) - 브랜드별 상품 조회
- `idx_product_name` (`name`) - 상품명 검색
- `idx_product_price` (`price`) - 가격 범위 조회
- `idx_product_created_at` (`created_at DESC`) - 최신순 정렬

---

## STOCK

**제약조건**

- PRIMARY KEY: `id`
- FOREIGN KEY: `product_id` → `PRODUCT(id)`
- UNIQUE: `product_id` (상품당 재고 1개만)

**인덱스**

- `idx_stock_product_id` (`product_id`)

---

## ORDER

**제약조건**

- PRIMARY KEY: `id`
- FOREIGN KEY: `user_id` → `USER(id)`

**인덱스**

- `idx_order_user_id` (`user_id`) - 사용자별 주문 조회
- `idx_order_status` (`status`) - 상태별 주문 조회
- `idx_order_created_at` (`created_at DESC`) - 최신순 정렬
- `idx_order_user_created` (`user_id`, `created_at DESC`) - 복합 인덱스

---

## ORDER_DETAIL

**제약조건**

- PRIMARY KEY: `id`
- FOREIGN KEY: `order_id` → `ORDER(id)`
- FOREIGN KEY: `product_id` → `PRODUCT(id)`
- FOREIGN KEY: `brand_id` → `BRAND(id)`

**인덱스**

- `idx_order_detail_order_id` (`order_id`) - 주문별 상세 조회
- `idx_order_detail_product_id` (`product_id`) - 상품별 주문 이력
- `idx_order_detail_brand_id` (`brand_id`) - 브랜드별 주문 이력

---

## PRODUCT_LIKE

**제약조건**

- PRIMARY KEY: `id`
- FOREIGN KEY: `product_id` → `PRODUCT(id)`
- FOREIGN KEY: `user_id` → `USER(id)`
- UNIQUE: (`user_id`, `product_id`) - 중복 좋아요 방지

**인덱스**

- `idx_product_like_user_id` (`user_id`) - 사용자별 좋아요 목록
- `idx_product_like_product_id` (`product_id`) - 상품별 좋아요 조회

---

## POINT

**제약조건**

- PRIMARY KEY: `id`
- FOREIGN KEY: `user_id` → `USER(id)`
- UNIQUE: `user_id` (사용자당 포인트 계정 1개만)

**인덱스**

- `idx_point_user_id` (`user_id`)

---
