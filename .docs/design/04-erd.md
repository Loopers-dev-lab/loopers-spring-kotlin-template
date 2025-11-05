### 전체 테이블 구조 및 관계 정리 ( ERD Mermaid 작성 가능 )

```mermaid
erDiagram
    products {
        bigint id PK
        varchar name
        bigint ref_brand_id FK
        bigint ref_stock_id FK
        bigint price
    }
    product_total_signals {
        bigint id PK
        bigint ref_product_id FK
        bigint like_count
    }
    brands {
        bigint id PK
        varchar name
    }
    stocks {
        bigint id PK
        bigint quantity
    }
    likes {
        bigint ref_member_id "PK, FK"
        bigint ref_product_id "PK, FK"
    }
    users {
        bigint id PK
        varchar loginId
        varchar email
        timestamp birthdate
    }
    points {
        bigint id PK
        bigint ref_user_id FK
        bigint balance
    }
    orders {
        bigint id PK
        bigint ref_user_id FK
        bigint total_price
        varchar status
    }
    orderItems {
        bigint id PK
        bigint ref_order_id FK
        bigint ref_product_id FK
        bigint quantity
        bigint price
    }

    users ||--|| points: "has"
    users ||--o{ likes: "likes"
    users ||--o{ orders: ""
    orders ||--o{ orderItems: ""
    orderItems }o--|| products: ""
    products ||--o{ likes: "likes"
    products }o--|| brands: "has"
    products ||--|| stocks: "has"
    products ||--|| product_total_signals: "has"
```