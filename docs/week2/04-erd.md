# ERD (Entity Relationship Diagram)

---

## ERD

```mermaid
erDiagram
    brands ||--o{ products : ""
    products ||--o{ likes : ""
    products ||--o{ order_items : ""
    orders ||--|{ order_items : ""

    brands {
        bigint id PK "AUTO_INCREMENT"
        varchar_100 name UK "NOT NULL"
        text description "NULL"
        timestamp created_at "NOT NULL, DEFAULT CURRENT_TIMESTAMP"
        timestamp updated_at "NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE"
    }

    products {
        bigint id PK "AUTO_INCREMENT"
        varchar_200 name "NOT NULL"
        text description "NULL"
        decimal price "NOT NULL, CHECK (price >= 0)"
        int stock_quantity "NOT NULL, DEFAULT 0, CHECK (stock_quantity >= 0)"
        bigint brand_id "NOT NULL, 논리적 FK"
        timestamp created_at "NOT NULL, DEFAULT CURRENT_TIMESTAMP"
        timestamp updated_at "NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE"
    }

    likes {
        bigint id PK "AUTO_INCREMENT"
        bigint user_id "NOT NULL"
        bigint product_id "NOT NULL, 논리적 FK"
        timestamp created_at "NOT NULL, DEFAULT CURRENT_TIMESTAMP"
    }

    orders {
        bigint id PK "AUTO_INCREMENT"
        bigint user_id "NOT NULL"
        varchar_20 status "NOT NULL, DEFAULT 'COMPLETED'"
        decimal total_amount "NOT NULL, CHECK (total_amount >= 0)"
        timestamp created_at "NOT NULL, DEFAULT CURRENT_TIMESTAMP"
        timestamp updated_at "NOT NULL, DEFAULT CURRENT_TIMESTAMP ON UPDATE"
    }

    order_items {
        bigint id PK "AUTO_INCREMENT"
        bigint order_id "NOT NULL, 논리적 FK"
        bigint product_id "NOT NULL, 스냅샷 참조"
        varchar_200 product_name "NOT NULL, 스냅샷"
        varchar_100 brand_name "NOT NULL, 스냅샷"
        decimal price "NOT NULL, 스냅샷"
        int quantity "NOT NULL, CHECK (quantity > 0)"
        decimal total_price "NOT NULL, 스냅샷"
        timestamp created_at "NOT NULL, DEFAULT CURRENT_TIMESTAMP"
    }
```
