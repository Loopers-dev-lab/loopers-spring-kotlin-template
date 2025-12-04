# Layer Guide

각 레이어별 구현 가이드입니다.

---

## Domain Layer

**위치**: `com.loopers.domain.<domain>/`

### Entity

도메인의 핵심 비즈니스 객체입니다.

```kotlin
@Entity
@Table(name = "orders")
class Order(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    fun complete() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 처리된 주문입니다")
        }
        status = OrderStatus.COMPLETED
    }

    fun cancel() {
        if (status != OrderStatus.PENDING) {
            throw CoreException(ErrorType.BAD_REQUEST, "취소할 수 없는 상태입니다")
        }
        status = OrderStatus.CANCELLED
    }
}
```

**규칙**:
- 비즈니스 로직은 Entity 내부에 캡슐화
- 상태 변경은 메서드를 통해서만
- Validation은 `CoreException` 사용 ([error-handling-guide.md](error-handling-guide.md) 참고)

### Enum

상태, 타입 등을 표현합니다.

```kotlin
enum class OrderStatus {
    PENDING,
    COMPLETED,
    CANCELLED
}
```

### Value Object

불변 값 객체입니다.

```kotlin
@Embeddable
data class Money(
    val amount: BigDecimal,
    val currency: String = "KRW"
) {
    init {
        if (amount < BigDecimal.ZERO) {
            throw CoreException(ErrorType.BAD_REQUEST, "금액은 0 이상이어야 합니다")
        }
    }

    operator fun plus(other: Money): Money {
        if (currency != other.currency) {
            throw CoreException(ErrorType.BAD_REQUEST, "통화가 다릅니다")
        }
        return Money(amount + other.amount, currency)
    }
}
```

### Domain Service

단일 도메인 내 비즈니스 로직입니다.

```kotlin
@Service
class OrderService(
    private val orderRepository: OrderRepository
) {
    fun createOrder(userId: Long, productId: Long): Order {
        val order = Order(userId = userId)
        return orderRepository.save(order)
    }

    fun getOrder(orderId: Long): Order {
        return orderRepository.findById(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "[id = $orderId] 주문을 찾을 수 없습니다")
    }
}
```

---

## Infrastructure Layer

**위치**: `com.loopers.infrastructure/<domain>/`

### Repository Interface

도메인 레이어에 위치하며, 데이터 접근 추상화입니다.

```kotlin
interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?
    fun findByUserId(userId: Long): List<Order>
}
```

### JPA Repository Implementation

인프라 레이어에 위치하며, 실제 구현입니다.

```kotlin
@Repository
class OrderJpaRepository(
    private val jpaRepository: OrderJpaRepositoryInterface
) : OrderRepository {

    override fun save(order: Order): Order {
        return jpaRepository.save(order)
    }

    override fun findById(id: Long): Order? {
        return jpaRepository.findByIdOrNull(id)
    }

    override fun findByUserId(userId: Long): List<Order> {
        return jpaRepository.findByUserId(userId)
    }
}

interface OrderJpaRepositoryInterface : JpaRepository<Order, Long> {
    fun findByUserId(userId: Long): List<Order>
}
```

---

## Application Layer

**위치**: `com.loopers.application/`

### Facade

여러 도메인 서비스를 조합합니다. 자세한 내용은 [architecture-guide.md](architecture-guide.md) 참고.

```kotlin
@Service
class OrderFacade(
    private val orderService: OrderService,
    private val productService: ProductService,
    private val pointService: PointService
) {
    // 원자성이 필요한 경우에만 @Transactional
    @Transactional
    fun placeOrder(command: PlaceOrderCommand): OrderResult {
        productService.decreaseStock(command.productId, command.quantity)

        if (command.usePoints > 0) {
            pointService.usePoints(command.userId, command.usePoints)
        }

        val order = orderService.createOrder(command.userId, command.productId)
        return OrderResult(order.id)
    }
}
```

---

## Interfaces Layer

**위치**: `com.loopers.interfaces.api/`

### Controller

HTTP 요청을 처리합니다.

```kotlin
@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderFacade: OrderFacade
) {
    @PostMapping
    fun createOrder(@RequestBody request: CreateOrderRequest): ApiResponse<OrderResponse> {
        val result = orderFacade.placeOrder(request.toCommand())
        return ApiResponse.success(OrderResponse.from(result))
    }
}
```

### DTO (Request/Response)

API 계약을 정의합니다.

```kotlin
data class CreateOrderRequest(
    val productId: Long,
    val quantity: Int,
    val usePoints: Long = 0
) {
    fun toCommand() = PlaceOrderCommand(
        productId = productId,
        quantity = quantity,
        usePoints = usePoints
    )
}

data class OrderResponse(
    val orderId: Long,
    val status: String
)
```

---

## 관련 문서

| 주제 | 문서 |
|------|------|
| 아키텍처 | [architecture-guide.md](architecture-guide.md) |
| 에러 처리 | [error-handling-guide.md](error-handling-guide.md) |
| 동시성 제어 | [concurrency-guide.md](concurrency-guide.md) |
