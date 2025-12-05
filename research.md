# Research: PaymentScheduler Refactoring

**Date**: 2025-12-06
**Specs Reviewed**:
- `docs/specs/요구사항_분석_문서_PG연동_Resilience.md`
- `docs/specs/솔루션_설계_문서_PG연동_Resilience.md`
- `docs/specs/상세_설계_문서_PG연동_Resilience.md`

---

## 1. Current PaymentStatusScheduler Structure

### Location and Dependencies

| Attribute | Value |
|-----------|-------|
| File | `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentStatusScheduler.kt` |
| Lines | L1-L221 |
| Layer | Infrastructure |

**Current Dependencies:**
```kotlin
class PaymentStatusScheduler(
    private val paymentService: PaymentService,      // Domain Service
    private val orderService: OrderService,          // Domain Service
    private val paymentResultHandler: PaymentResultHandler,  // Application Handler
    private val pgClient: PgClient,                  // Infrastructure Client
    private val transactionTemplate: TransactionTemplate,
)
```

**Current Flow:**
1. `@Scheduled(fixedRate = 60_000)` - Runs every 1 minute
2. Finds IN_PROGRESS payments older than 1 minute via `paymentService.findInProgressPayments(threshold)`
3. For each payment, queries PG status via `pgClient.getPaymentsByOrderId(userId, orderId)`
4. Based on PG result, calls `paymentResultHandler.handlePaymentSuccess/handlePaymentFailure`
5. Force fails payments older than 5 minutes if still PENDING at PG

**Issues with Current Design:**
- Scheduler directly depends on `PgClient` (infrastructure layer)
- Scheduler directly depends on `OrderService` (domain layer) to get orderItems
- Internal `PgQueryResult` sealed class exists in infrastructure layer
- Scheduler handles transaction management via `TransactionTemplate`

---

## 2. Integration Points

### 2.1 PaymentStatusScheduler (To Rename/Refactor)

| Attribute | Value |
|-----------|-------|
| File | `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentStatusScheduler.kt` |
| Test File | `/apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/payment/PaymentStatusSchedulerTest.kt` |
| Method | `checkInProgressPayments()` |
| Lines | L43-L85 |

**Key Methods:**
- `checkInProgressPayments()` - L43-L85 (scheduled method)
- `processPayment(paymentId, userId, orderId, createdAt)` - L87-L168 (private)
- `queryPgStatus(userId, orderId)` - L170-L199 (private, PG query logic)
- `getOrderItems(orderId)` - L201-L209 (private, gets order items for recovery)

### 2.2 PaymentResultHandler (Already in Application Layer)

| Attribute | Value |
|-----------|-------|
| File | `/apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentResultHandler.kt` |
| Lines | L1-L125 |
| Annotation | `@Component` |

**Methods:**
```kotlin
@Transactional
fun handlePaymentSuccess(paymentId: Long, externalPaymentKey: String? = null)  // L38-L46

@Transactional
fun handlePaymentFailure(paymentId: Long, reason: String?, orderItems: List<OrderItemInfo>?)  // L60-L86
```

**Dependencies:**
- `PaymentService`, `OrderService`, `PointService`, `CouponService`, `ProductService`

### 2.3 PaymentService (Domain Layer)

| Attribute | Value |
|-----------|-------|
| File | `/apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentService.kt` |
| Lines | L1-L145 |

**Key Methods for Scheduler:**
```kotlin
@Transactional(readOnly = true)
fun findInProgressPayments(threshold: ZonedDateTime): List<Payment>  // L112-L118

@Transactional(readOnly = true)
fun findById(paymentId: Long): Payment  // L139-L143
```

### 2.4 PgClient (Infrastructure Layer)

| Attribute | Value |
|-----------|-------|
| Interface | `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClient.kt` |
| Implementation | `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClientImpl.kt` |

**Key Method:**
```kotlin
fun getPaymentsByOrderId(userId: Long, orderId: String): PgPaymentListResponse  // L28-L31
```

---

## 3. Patterns to Follow

### 3.1 Facade Pattern

**Reference**: `/apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt`

| Characteristic | Example |
|----------------|---------|
| Annotation | `@Component` |
| Dependencies | Multiple Services + Infrastructure (PgClient, CacheTemplate) |
| Transaction | Uses `TransactionTemplate` for programmatic TX control |
| No @Transactional | Methods do NOT use declarative @Transactional |

**OrderFacade Structure (L33-L45):**
```kotlin
@Component
class OrderFacade(
    val productService: ProductService,
    val orderService: OrderService,
    val pointService: PointService,
    val couponService: CouponService,
    val paymentService: PaymentService,
    val pgClient: PgClient,
    val paymentResultHandler: PaymentResultHandler,
    private val transactionTemplate: TransactionTemplate,
    private val cacheTemplate: CacheTemplate,
    ...
)
```

**Transaction Pattern in OrderFacade:**
```kotlin
// L313-L315 - Uses transactionTemplate for TX boundaries
transactionTemplate.execute { _ ->
    paymentResultHandler.handlePaymentSuccess(...)
}
```

**Payment Result Method in OrderFacade (L358-L374):**
```kotlin
fun handlePaymentResult(paymentId: Long, isSuccess: Boolean, transactionKey: String?, reason: String?) {
    val payment = paymentService.findById(paymentId)

    if (isSuccess) {
        paymentResultHandler.handlePaymentSuccess(paymentId, transactionKey)
    } else {
        val order = orderService.findById(payment.orderId)
        val orderItems = order.orderItems.map { ... }
        paymentResultHandler.handlePaymentFailure(paymentId, reason, orderItems)
    }
}
```

### 3.2 Simple Facade Pattern (No External Calls)

**Reference**: `/apps/commerce-api/src/main/kotlin/com/loopers/application/like/LikeFacade.kt`

```kotlin
@Component
class LikeFacade(
    private val likeService: ProductLikeService,
    private val productService: ProductService,
) {
    @Transactional
    fun addLike(userId: Long, productId: Long) { ... }
}
```

- Uses declarative `@Transactional` when no external calls involved

### 3.3 Scheduler Pattern

**Current Pattern** (in PaymentStatusScheduler L42-L85):
```kotlin
@Scheduled(fixedRate = 60_000)
fun checkInProgressPayments() {
    val inProgressPayments = paymentService.findInProgressPayments(threshold)

    for (payment in inProgressPayments) {
        try {
            processPayment(...)
        } catch (e: ObjectOptimisticLockingFailureException) {
            // Skip - already processed by callback
        } catch (e: Exception) {
            logger.error(...)
        }
    }
}
```

**Key Design Decisions:**
- Individual payment processing with try-catch
- Optimistic lock conflicts are logged at DEBUG level and skipped
- Other exceptions logged at ERROR level

### 3.4 PG Query Result Pattern

**Current Location**: PaymentStatusScheduler L214-L220 (private sealed class)

```kotlin
private sealed class PgQueryResult {
    data class Success(val transactionKey: String) : PgQueryResult()
    data class Failed(val reason: String) : PgQueryResult()
    data object Pending : PgQueryResult()
    data object NotFound : PgQueryResult()
    data class QueryFailed(val reason: String) : PgQueryResult()
}
```

This pattern will need to move to Facade or be exposed publicly.

---

## 4. Reference Implementations

### 4.1 PG Exception Handling

**Location**: `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgException.kt` (L1-L45)

```kotlin
sealed class PgException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class RequestNotReached(message: String, cause: Throwable? = null) : PgException(message, cause)
    class ResponseUncertain(message: String, cause: Throwable? = null) : PgException(message, cause)
    class CircuitOpen(message: String) : PgException(message)
    class BusinessError(val errorCode: String, message: String) : PgException(message)
}
```

### 4.2 Compensation Transaction (Resource Recovery)

**Location**: `/apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentResultHandler.kt` L91-L115

```kotlin
private fun recoverResources(payment: Payment, orderItems: List<OrderItemInfo>?) {
    // 1. Point recovery
    if (payment.usedPoint > Money.ZERO_KRW) {
        pointService.restore(payment.userId, payment.usedPoint)
    }

    // 2. Coupon recovery
    payment.issuedCouponId?.let { couponId ->
        couponService.cancelCouponUse(couponId)
    }

    // 3. Stock recovery
    orderItems?.let { items ->
        val increaseUnits = items.map { ... }
        productService.increaseStocks(...)
    }
}
```

### 4.3 Optimistic Lock Handling

**Current Usage**: PaymentStatusScheduler L64-L70

```kotlin
catch (e: ObjectOptimisticLockingFailureException) {
    logger.debug(
        "결제 상태 확인 중 낙관적 락 충돌 - paymentId: {} (이미 다른 곳에서 처리됨)",
        payment.id,
    )
    skippedCount++
}
```

**Payment Entity Version Field**: `/apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` L73-L76

```kotlin
@Version
@Column(name = "version", nullable = false)
var version: Long = 0
    private set
```

---

## 5. Error Types

**Location**: `/apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt`

```kotlin
enum class ErrorType(val status: HttpStatus, val code: String, val message: String) {
    /** Generic Errors */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ...),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, ...),
    NOT_FOUND(HttpStatus.NOT_FOUND, ...),
    CONFLICT(HttpStatus.CONFLICT, ...),

    /** PG Related Errors */
    CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN", "일시적으로 결제 서비스를 이용할 수 없습니다."),
    PAYMENT_IN_PROGRESS(HttpStatus.ACCEPTED, "PAYMENT_IN_PROGRESS", "결제가 진행 중입니다."),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED", "결제에 실패했습니다."),
}
```

---

## 6. Considerations for Planner

### 6.1 New PaymentFacade Creation

- Currently no `PaymentFacade` exists
- Need to create `/apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentFacade.kt`
- Move scheduler-related logic from infrastructure to application layer

### 6.2 Logic to Move to PaymentFacade

From current PaymentStatusScheduler:
1. `queryPgStatus()` logic (L170-L199) - queries PG and interprets result
2. `getOrderItems()` logic (L201-L209) - gets order items for recovery
3. Decision logic in `processPayment()` (L97-L167) - when to succeed/fail/wait

### 6.3 New PaymentScheduler Responsibilities

After refactoring, scheduler should only:
1. Find IN_PROGRESS payments via Facade
2. Call Facade method for each payment
3. Handle exceptions (optimistic lock, general errors)

### 6.4 Method Signature Proposal for PaymentFacade

```kotlin
// Option 1: One-by-one processing
fun findInProgressPaymentsToCheck(threshold: ZonedDateTime): List<PaymentInfo.InProgress>
fun processInProgressPayment(paymentId: Long)

// Option 2: Combined (current pattern simplified)
fun checkAndFinalizePayment(paymentId: Long, userId: Long, orderId: Long, createdAt: ZonedDateTime)
```

### 6.5 PgQueryResult Visibility

- Currently private in PaymentStatusScheduler
- Options:
  1. Create as sealed class in Facade or domain package
  2. Handle internally in Facade, return simple result (success/failure/pending)

### 6.6 Transaction Boundaries

Per spec design doc (L167-L181):
- Each payment should be processed in individual transaction
- Optimistic lock conflicts should skip that payment, continue others
- PaymentFacade methods should NOT use `@Transactional` annotation
- Use `TransactionTemplate` for programmatic transaction control

### 6.7 Test Updates Required

| Test File | Reason |
|-----------|--------|
| `PaymentStatusSchedulerTest.kt` | Rename to PaymentSchedulerTest, update to use PaymentFacade |

---

## 7. Files Examined

| File | Relevance |
|------|-----------|
| `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentStatusScheduler.kt` | Current scheduler implementation to refactor |
| `/apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/payment/PaymentStatusSchedulerTest.kt` | Test to update |
| `/apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` | Reference Facade pattern with PG calls |
| `/apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentResultHandler.kt` | Existing handler to reuse |
| `/apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentService.kt` | Domain service methods |
| `/apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderService.kt` | Domain service for order lookup |
| `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClient.kt` | PG interface |
| `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClientImpl.kt` | PG implementation with resilience |
| `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgException.kt` | PG exception types |
| `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgDto.kt` | PG DTOs including PgTransactionStatus |
| `/apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` | Payment entity with @Version |
| `/apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentRepository.kt` | Repository interface |
| `/apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt` | Error type enum |
| `docs/specs/상세_설계_문서_PG연동_Resilience.md` | Design spec for scheduler behavior |

---

## 8. Proposed File Structure After Refactoring

```
apps/commerce-api/src/main/kotlin/com/loopers/
├── application/
│   └── payment/
│       └── PaymentFacade.kt           # NEW - Contains PG query logic, decision logic
├── infrastructure/
│   └── payment/
│       └── PaymentScheduler.kt        # RENAMED from PaymentStatusScheduler
│                                      # Now only depends on PaymentFacade
```
