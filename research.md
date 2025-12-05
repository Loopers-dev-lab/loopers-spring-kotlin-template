# Research: PG 연동 Resilience

**Date**: 2025-12-06
**Specs Reviewed**:
- `docs/specs/요구사항_분석_문서_PG연동_Resilience.md`
- `docs/specs/솔루션_설계_문서_PG연동_Resilience.md`
- `docs/specs/도메인_모델링_문서_PG연동_Resilience.md`
- `docs/specs/상세_설계_문서_PG연동_Resilience.md`
- `docs/specs/PG_SIMULATOR_API.md`

---

## 1. Integration Points

### 1.1 Payment Entity (Core Changes)

| Attribute | Value |
|-----------|-------|
| File | `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` |
| Lines | L1-L107 |

**Current State**:
- Only supports point-only payments (instant PAID status)
- Missing: `paidAmount`, `externalPaymentKey`, `failureMessage` fields
- Missing: `start()`, `updateExternalPaymentKey()`, `success()`, `fail()` methods

**Current Factory Method**:
```kotlin
fun pay(userId: Long, order: Order, usedPoint: Money, issuedCouponId: Long?, couponDiscount: Money): Payment
```
Creates Payment with `PaymentStatus.PAID` directly.

**Modification Points**:
- Add new fields: `paidAmount: Money`, `externalPaymentKey: String?`, `failureMessage: String?`
- Add new methods for state transitions
- Modify factory to create PENDING status initially
- Add validation for `usedPoint + paidAmount + couponDiscount == totalAmount`

---

### 1.2 PaymentStatus Enum

| Attribute | Value |
|-----------|-------|
| File | `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentStatus.kt` |
| Lines | L1-L6 |

**Current Values**:
```kotlin
enum class PaymentStatus {
    PAID,
}
```

**Required New Values**: `PENDING`, `IN_PROGRESS`, `FAILED`

---

### 1.3 Order Entity

| Attribute | Value |
|-----------|-------|
| File | `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Order.kt` |
| Lines | L1-L99 |

**Current Methods**:
- `place(userId: Long): Order` - L60-L62
- `pay()` - L86-L98

**Required New Method**: `cancel()` - PLACED -> CANCELLED

---

### 1.4 OrderStatus Enum

| Attribute | Value |
|-----------|-------|
| File | `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderStatus.kt` |
| Lines | L1-L6 |

**Current Values**:
```kotlin
enum class OrderStatus {
    PLACED,
    PAID,
}
```

**Required New Value**: `CANCELLED`

---

### 1.5 PointAccount Entity

| Attribute | Value |
|-----------|-------|
| File | `apps/commerce-api/src/main/kotlin/com/loopers/domain/point/PointAccount.kt` |
| Lines | L1-L73 |

**Current Methods**:
- `charge(amount: Money)` - L54-L60
- `deduct(amount: Money)` - L62-L72

**Required New Method**: `restore(amount: Money)` - for refund on payment failure

---

### 1.6 IssuedCoupon Entity

| Attribute | Value |
|-----------|-------|
| File | `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/IssuedCoupon.kt` |
| Lines | L1-L83 |

**Current State**:
- Has `@Version` for optimistic locking (L54-L57)
- `use(userId, coupon, usedAt)` - L67-L82

**Required New Method**: `cancelUse()` - USED -> AVAILABLE, reset `usedAt`

---

### 1.7 OrderFacade (Orchestration Layer)

| Attribute | Value |
|-----------|-------|
| File | `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` |
| Lines | L1-L125 |
| Key Method | `placeOrder(criteria: OrderCriteria.PlaceOrder): OrderInfo.PlaceOrder` L60-L123 |

**Current Flow** (single transaction):
1. Stock decrease (`productService.decreaseStocks`)
2. Coupon use (`couponService.useCoupon`)
3. Point deduct (`pointService.deduct`)
4. Order place + pay (`orderService.place`, `orderService.pay`)
5. Cache update

**Required Changes**:
- Split into two transactions per spec
- **Transaction 1**: Stock -> Coupon -> Point -> Payment PENDING -> Order
- **Transaction 2** (if PG fails): Restore point, cancel coupon use, increase stock, cancel order
- Add PG client call between transactions
- Add support for callback/scheduler result handling

---

### 1.8 PaymentRepository

| Attribute | Value |
|-----------|-------|
| Interface | `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentRepository.kt` |
| Implementation | `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/PaymentRdbRepository.kt` |
| JPA Repository | `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/PaymentJpaRepository.kt` |

**Current Methods**:
- `findByOrderId(orderId: Long): Payment?`
- `save(payment: Payment): Payment`

**Required New Methods**:
- `findByIdWithLock(id: Long): Payment?` - for callback/scheduler concurrency
- `findInProgressPaymentIds(): List<Long>` - for scheduler batch processing
- `findByExternalPaymentKey(key: String): Payment?` - for callback lookup

---

## 2. Patterns to Follow

### 2.1 Entity Pattern

**Reference**: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/IssuedCoupon.kt`

- Extends `BaseEntity` (provides id, createdAt, updatedAt, deletedAt)
- `@Version` annotation for optimistic locking: L54-L57
- State change methods throw `CoreException` on precondition failure
- Private setters with state validation

**BaseEntity Location**: `modules/jpa/src/main/kotlin/com/loopers/domain/BaseEntity.kt`

---

### 2.2 Repository Pattern

**Interface**: `apps/commerce-api/src/main/kotlin/com/loopers/domain/point/PointAccountRepository.kt`
**Implementation**: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/point/PointAccountRdbRepository.kt`
**JPA Repository**: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/point/PointAccountJpaRepository.kt`

**Pessimistic Lock Pattern** (L13-L15 in JPA Repository):
```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM PointAccount p WHERE p.userId = :userId")
fun findByUserIdWithLock(@Param("userId") userId: Long): PointAccount?
```

---

### 2.3 Service/Facade Pattern

**Facade Reference**: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt`

- Uses `TransactionTemplate` for programmatic transaction control (L29)
- RetryTemplate for optimistic lock retry (L33-L58)
- Cross-domain orchestration (ProductService, OrderService, PointService, CouponService)

**Service Reference**: `apps/commerce-api/src/main/kotlin/com/loopers/domain/point/PointService.kt`

- `@Component` annotation
- `@Transactional` on methods
- Single domain operations

---

### 2.4 Error Handling

**Location**: `apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt`

```kotlin
enum class ErrorType(val status: HttpStatus, val code: String, val message: String) {
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase, "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.reasonPhrase, "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.reasonPhrase, "이미 존재하는 리소스입니다."),
}
```

**New Types Likely Needed**:
- `CIRCUIT_OPEN` - for circuit breaker open state
- `PAYMENT_IN_PROGRESS` - for timeout/uncertain state
- `PAYMENT_FAILED` - for PG rejection

**CoreException Usage**:
```kotlin
throw CoreException(ErrorType.BAD_REQUEST, "사용 포인트는 0 이상이어야 합니다.")
```

---

### 2.5 Test Patterns

**Unit Test Reference**: `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/PaymentTest.kt`
- JUnit 5 with `@Nested` for grouping
- `@DisplayName` for Korean descriptions
- `assertThrows<CoreException>` for exception testing
- `ReflectionTestUtils.setField` for setting entity IDs

**Integration Test Reference**: `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderServiceIntegrationTest.kt`
- `@SpringBootTest`
- `@Autowired` constructor injection
- `DatabaseCleanUp.truncateAllTables()` in `@AfterEach`
- Factory methods for test data creation

**E2E Test Reference**: `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/order/OrderV1ApiE2ETest.kt`
- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)`
- `TestRestTemplate` for HTTP calls
- Headers with `X-USER-ID`
- `ParameterizedTypeReference` for generic response types

---

## 3. Reference Implementations

### 3.1 HTTP Client (RestTemplate)

**Location**: `apps/pg-simulator/src/main/kotlin/com/loopers/infrastructure/payment/PaymentCoreRelay.kt`

```kotlin
private val restTemplate = RestTemplate()

override fun notify(callbackUrl: String, transactionInfo: TransactionInfo) {
    runCatching {
        restTemplate.postForEntity(callbackUrl, transactionInfo, Any::class.java)
    }.onFailure { e -> logger.error("콜백 호출을 실패했습니다. {}", e.message, e) }
}
```

**Note**: No existing Resilience4j usage found in codebase. New implementation needed.

---

### 3.2 Retry Pattern (Spring Retry)

**Location**: `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` L36-L58

```kotlin
this.retryTemplate = RetryTemplate.builder()
    .maxAttempts(2)
    .uniformRandomBackoff(100, 500)
    .retryOn(ObjectOptimisticLockingFailureException::class.java)
    .withListener(...)
    .build()
```

**Dependencies in build.gradle.kts**:
```kotlin
implementation("org.springframework.retry:spring-retry")
implementation("org.springframework:spring-aspects")
```

---

### 3.3 Pessimistic Lock Pattern

**Location**: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/product/ProductJpaRepository.kt` L13-L15

```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id IN :ids")
fun findAllByIdsWithLock(ids: List<Long>): List<Product>
```

---

### 3.4 Optimistic Lock Pattern

**Location**: `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/IssuedCoupon.kt` L54-L57

```kotlin
@Version
@Column(name = "version", nullable = false)
var version: Long = 0
    private set
```

---

## 4. Infrastructure Components to Create

### 4.1 PgClient (New)

**Purpose**: HTTP client for PG API calls with Resilience4j
**Location**: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PgClient.kt`

**Methods Needed**:
- `requestPayment(orderId: String, cardType: String, cardNo: String, amount: Long, callbackUrl: String): PgResponse`
- `getPaymentByTransactionKey(transactionKey: String): PgPaymentInfo`
- `getPaymentsByOrderId(orderId: String): List<PgTransaction>`

---

### 4.2 PaymentStatusScheduler (New)

**Purpose**: Scheduled job for confirming IN_PROGRESS payments
**Location**: `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentStatusScheduler.kt`

**Note**: No existing `@Scheduled` usage found. New scheduler infrastructure needed.

---

### 4.3 PaymentWebhookController (New)

**Purpose**: Receive PG callbacks
**Location**: `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/payment/PaymentWebhookController.kt`

---

## 5. Configuration Requirements

### 5.1 Dependencies to Add (build.gradle.kts)

```kotlin
// Resilience4j
implementation("io.github.resilience4j:resilience4j-spring-boot3")
implementation("io.github.resilience4j:resilience4j-micrometer")

// WebClient (if using reactive HTTP client)
implementation("org.springframework.boot:spring-boot-starter-webflux")
```

### 5.2 Application Configuration (application.yml)

New sections needed for:
- PG base URL configuration
- Resilience4j circuit breaker settings
- Scheduler cron expression
- Timeout settings

---

## 6. Considerations for Planner

### 6.1 Transaction Boundary Changes

The current OrderFacade uses a single transaction. Per spec:
- **Transaction 1**: Resource allocation (stock, coupon, point) + Payment PENDING + Order
- **PG Call**: Outside transaction with Resilience4j
- **Transaction 2**: Recovery on failure (restore resources, cancel order)

This requires significant refactoring of `OrderFacade.placeOrder()`.

### 6.2 New Domain Services Needed

- `PaymentService`: Handle Payment state transitions, PG result processing
- Methods: `createPending()`, `start()`, `complete()`, `fail()`, `findInProgressPayments()`

### 6.3 Callback/Scheduler Concurrency

Both callback and scheduler may process same payment. Use:
- `@Version` on Payment for optimistic lock (spec mentions this)
- `findByIdWithLock()` for pessimistic lock option
- Catch `OptimisticLockingFailureException` and skip

### 6.4 Request/Response DTOs

Need new DTOs for:
- PG API request/response (`PgPaymentRequest`, `PgPaymentResponse`, etc.)
- Callback webhook (`PaymentCallbackRequest`)
- Extended order response with payment status

### 6.5 OrderCriteria Changes

Current `OrderCriteria.PlaceOrder`:
```kotlin
data class PlaceOrder(
    val userId: Long,
    val usePoint: Money,
    val items: List<PlaceOrderItem>,
    val issuedCouponId: Long?,
)
```

Needs additional fields for card payment:
- `cardType: String?`
- `cardNo: String?`
- Logic to determine `PaymentMethod` (POINT, CARD, MIXED)

### 6.6 Database Schema Changes

```sql
ALTER TABLE payment
    ADD COLUMN paid_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN external_payment_key VARCHAR(100),
    ADD COLUMN failure_message VARCHAR(500);

-- Add version column if not exists
ALTER TABLE payment ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_payment_status_updated_at ON payment (status, updated_at);
CREATE UNIQUE INDEX idx_payment_external_payment_key ON payment (external_payment_key) WHERE external_payment_key IS NOT NULL;
```

### 6.7 PG Simulator Integration

PG Simulator is available at `apps/pg-simulator/` with:
- Base URL: `{{pg-simulator}}/api/v1/payments`
- Required header: `X-USER-ID`
- 40% failure rate simulation
- 100-500ms random delay
- Callback mechanism

---

## 7. Files Examined

| File | Relevance |
|------|-----------|
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` | Core entity to modify |
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentStatus.kt` | Enum to extend |
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Order.kt` | Add cancel() method |
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderStatus.kt` | Add CANCELLED status |
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/point/PointAccount.kt` | Add restore() method |
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/IssuedCoupon.kt` | Add cancelUse() method |
| `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` | Major refactoring point |
| `apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt` | Add new error types |
| `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/point/PointAccountJpaRepository.kt` | Pessimistic lock pattern reference |
| `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/Stock.kt` | Has increase() method for rollback |
| `apps/commerce-api/build.gradle.kts` | Dependencies to add |
| `apps/pg-simulator/src/main/kotlin/com/loopers/infrastructure/payment/PaymentCoreRelay.kt` | RestTemplate usage reference |
| `modules/jpa/src/main/kotlin/com/loopers/domain/BaseEntity.kt` | Base entity pattern |
| `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/PaymentTest.kt` | Unit test pattern |
| `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeIntegrationTest.kt` | Integration test pattern |
| `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/order/OrderV1ApiE2ETest.kt` | E2E test pattern |
