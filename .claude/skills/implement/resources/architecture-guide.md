# Architecture Guide

## Layered Architecture

```
HTTP Request
    ↓
Interfaces Layer (Controllers, DTOs)
    ↓
Application Layer (Facades)
    ↓
Domain Layer (Services, Entities)
    ↓
Infrastructure Layer (Repositories)
    ↓
Database (PostgreSQL)
```

**핵심 원칙:** 각 레이어는 하나의 책임만 가집니다.

---

## Layer Dependencies

**Dependency Direction:**

```
[External Layer]                    [Internal Layer - Core]
interfaces/  ─────→  application/  ─────→  domain/
                                              ↑
                                              │ (implements)
                                     infrastructure/
```

**Rules:**

- Arrow(→) = dependency direction (A → B means "A depends on B")
- Dependencies flow from external to internal only (reverse prohibited)
- `infrastructure/` implements Repository interfaces defined in `domain/` (DIP)
- `domain/` never directly references `infrastructure/`
- Horizontal dependencies within the same layer prohibited

**Request Flow:**

```
HTTP Request → Controller → Facade → Service → RepositoryImpl → DB
(interfaces)   (interfaces) (application) (domain)  (infrastructure)
```

---

## Service vs Facade Pattern

### Service

**역할**: 단일 도메인 비즈니스 로직

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository
) {
    // 자신의 도메인만 처리
    fun createOrder(command: CreateOrderCommand): Order {
        return orderRepository.save(Order.create(command))
    }
}
```

**규칙**:

- 자신의 도메인만 처리
- 다른 Service 호출 불가
- Repository는 자신의 도메인만 주입

### Facade

**역할**: 여러 Service 오케스트레이션

```kotlin
@Service
class OrderFacade(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val pointService: PointService
) {
    // 원자성이 필요한 경우에만 @Transactional 사용
    @Transactional
    fun placeOrder(command: PlaceOrderCommand): OrderResult {
        productService.decreaseStock(command.productId, command.quantity)
        pointService.usePoints(command.userId, command.pointAmount)
        return orderService.createOrder(command.toCreateCommand())
    }

    // 조회만 하는 경우 트랜잭션 불필요
    fun getOrderSummary(orderId: Long): OrderSummary {
        val order = orderService.getOrder(orderId)
        val product = productService.getProduct(order.productId)
        return OrderSummary(order, product)
    }
}
```

**규칙**:

- 여러 Service 조합 가능
- 다른 Facade 호출 불가
- 원자성이 필요한 경우에만 `@Transactional` 사용

---

## Transaction Boundaries

### 언제 @Transactional을 사용하는가?

**사용하는 경우:**

- 여러 도메인에 걸친 쓰기 작업이 원자적으로 처리되어야 할 때
- 하나라도 실패하면 전체 롤백이 필요할 때

**사용하지 않는 경우:**

- 단순 조회 작업
- 단일 도메인 내 독립적인 작업

### 롤백 처리

```kotlin
@Transactional
fun placeOrder(command: PlaceOrderCommand): OrderResult {
    productService.decreaseStock(...)  // 성공
    pointService.usePoints(...)        // 실패 시 전체 롤백
    return orderService.createOrder(...)
}
```

RuntimeException 발생 시 자동 롤백됩니다.

---

## Module Dependencies

```
apps/           → modules/, supports/
modules/        → supports/ (선택적)
supports/       → 외부 라이브러리만
```

- `apps/`: 실행 가능한 Spring Boot 애플리케이션
- `modules/`: 재사용 가능한 도메인 무관 설정 (JPA, Redis, Kafka)
- `supports/`: 횡단 관심사 (Jackson, Logging, Monitoring)

---

## 참고 자료

- CLAUDE.md: Project architecture and conventions
