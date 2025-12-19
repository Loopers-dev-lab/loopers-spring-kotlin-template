# ProductStatistic 도메인 모델링 문서

> **Round 8**: Kafka 기반 이벤트 파이프라인

## 1. 클래스 다이어그램

```mermaid
classDiagram
    class ProductStatistic {
        <<Aggregate Root>>
        -Long id
        -Long productId
        -Long likeCount
        -Long salesCount
        -Long viewCount
        +increaseLikeCount(amount: Long)
        +decreaseLikeCount(amount: Long)
        +increaseSalesCount(amount: Long)
        +increaseViewCount(amount: Long)
    }

    class OrderPaidEventV1 {
        <<Domain Event>>
        -Long orderId
        -List~OrderItemSnapshot~ orderItems
        -Instant occurredAt
    }

    class OrderItemSnapshot {
        <<Value Object>>
        -Long productId
        -Int quantity
    }

    class ProductViewedEventV1 {
        <<Domain Event>>
        -Long productId
        -Instant occurredAt
    }

    class StockDepletedEventV1 {
        <<Domain Event>>
        -Long productId
        -Instant occurredAt
    }

    class DomainEvent {
        <<Interface>>
        +Instant occurredAt
    }

    ProductStatistic ..> Product : ID 참조 (간접)
    OrderPaidEventV1 *-- OrderItemSnapshot : 합성
    OrderPaidEventV1 ..|> DomainEvent : 구현
    ProductViewedEventV1 ..|> DomainEvent : 구현
    StockDepletedEventV1 ..|> DomainEvent : 구현
```

### 주요 구조

**Aggregate: ProductStatistic**

ProductStatistic은 상품별 메트릭(좋아요 수, 판매량, 조회수)을 집계하는 독립 Aggregate이다. Product와 별도 생명주기를 가지며, productId로 간접 참조한다. 배치 처리를 위해 각 메트릭 증감 메서드는 amount 파라미터를 받는다.

**Domain Events**

OrderPaidEventV1은 결제 완료 시 발행되며, 판매량 집계를 위해 OrderItemSnapshot 목록을 포함한다. ProductViewedEventV1은 상품 상세 조회 시 발행되며, 조회수 집계에 사용된다. StockDepletedEventV1은 재고 소진 시 발행되며, 상품 캐시 무효화에 사용된다. 기존 LikeCreatedEventV1, LikeCanceledEventV1은 좋아요 집계에 사용된다.

---

## 2. 도메인 규칙

### 2.1 생성 규칙

**ProductStatistic.create(productId: Long)**

상품 생성 시 ProductStatistic도 함께 생성되며, 모든 메트릭 초기값은 0이다.

| 필드 | 초기값 |
|------|--------|
| likeCount | 0 |
| salesCount | 0 |
| viewCount | 0 |

### 2.2 메트릭 증가 규칙

`increaseLikeCount(amount)`, `increaseSalesCount(amount)`, `increaseViewCount(amount)`는 동일한 규칙을 따른다.

**사전조건**

- amount > 0

**동작**

- 해당 메트릭 += amount

**예외**

- amount ≤ 0: "증가량은 0보다 커야 합니다"

### 2.3 좋아요 감소 규칙

**decreaseLikeCount(amount: Long)**

**사전조건**

- amount > 0
- likeCount - amount ≥ 0

**동작**

- likeCount -= amount

**예외**

- amount ≤ 0: "감소량은 0보다 커야 합니다"
- likeCount - amount < 0: "좋아요 수는 0 미만이 될 수 없습니다"

### 2.4 불변식

ProductStatistic의 모든 메트릭은 항상 0 이상이어야 한다.

- likeCount ≥ 0
- salesCount ≥ 0
- viewCount ≥ 0

---

## 3. 상태 다이어그램

ProductStatistic은 별도의 상태 전이가 없고 메트릭 값만 변경되므로 상태 다이어그램을 생략한다.

---

## 4. 도메인 이벤트

### 현재 사용 여부

사용한다. Kafka 기반 이벤트 파이프라인 구축이 이번 라운드의 핵심 목표이며, Transactional Outbox Pattern을 통해 이벤트를 발행하고 commerce-streamer 모듈에서 소비하여 메트릭을 집계한다.

### 이벤트 목록

**발행 측**

| 이벤트명 | 발행 주체 | 트리거 조건 |
|----------|-----------|-------------|
| LikeCreatedEventV1 | LikeService | 좋아요 등록 성공 후 |
| LikeCanceledEventV1 | LikeService | 좋아요 취소 성공 후 |
| OrderPaidEventV1 | PaymentFacade | 결제 완료 처리 후 |
| ProductViewedEventV1 | ProductFacade | 상품 상세 조회 시 |
| StockDepletedEventV1 | StockService | Stock.decrease() 후 quantity == 0일 때 |

**소비 측**

| 이벤트명 | 소비 주체 | 처리 내용 |
|----------|-----------|-----------|
| LikeCreatedEventV1 | ProductMetricsEventConsumer | ProductStatistic.increaseLikeCount() 호출 |
| LikeCanceledEventV1 | ProductMetricsEventConsumer | ProductStatistic.decreaseLikeCount() 호출 |
| OrderPaidEventV1 | ProductMetricsEventConsumer | ProductStatistic.increaseSalesCount() 호출 (상품별) |
| ProductViewedEventV1 | ProductMetricsEventConsumer | ProductStatistic.increaseViewCount() 호출 |
| StockDepletedEventV1 | ProductCacheEventConsumer | 상품 캐시 무효화 |

**페이로드**

| 이벤트명 | 필드 | 설명 |
|----------|------|------|
| LikeCreatedEventV1 | userId, productId, occurredAt | 기존 구조 유지 |
| LikeCanceledEventV1 | userId, productId, occurredAt | 기존 구조 유지 |
| OrderPaidEventV1 | orderId, orderItems(productId, quantity), occurredAt | 상품별 수량 스냅샷 포함 |
| ProductViewedEventV1 | productId, occurredAt | 조회된 상품 식별 |
| StockDepletedEventV1 | productId, occurredAt | 재고 소진된 상품 식별 |

참고: 발행과 소비는 독립적으로 개발/테스트/배포될 수 있음. 이벤트 페이로드가 둘을 연결하는 계약.
