# Research: PG Client Implementation (Domain Layer)

**Date**: 2025-12-06
**Specs Reviewed**: `docs/specs/도메인_모델링_문서_PG연동_Resilience.md`

---

## 1. Current State Analysis

### 1.1 PgClient Already Exists in Infrastructure Layer

The spec requests placing PgClient interface in the domain layer, but currently:

| Component | Location | Status |
|-----------|----------|--------|
| PgClient interface | `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClient.kt` | Exists in infrastructure |
| PgClientImpl | `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClientImpl.kt` | Exists |
| PgException | `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgException.kt` | Exists (sealed class) |
| PgDto (Request/Response) | `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgDto.kt` | Exists |

**Current PgClient Interface** (L6-L32):
```kotlin
interface PgClient {
    fun requestPayment(userId: Long, request: PgPaymentRequest): PgPaymentResponse
    fun getPaymentByKey(userId: Long, transactionKey: String): PgPaymentDetailResponse
    fun getPaymentsByOrderId(userId: Long, orderId: String): PgPaymentListResponse
}
```

### 1.2 Gap Analysis: Spec vs Current Implementation

| Spec Requirement | Current State | Gap |
|-----------------|---------------|-----|
| PgClient in domain layer | In infrastructure layer | Need to move/create domain interface |
| PgPaymentRequest (Value Object) | PgPaymentRequest exists but as simple DTO | Need domain value object with validation |
| CardInfo (Value Object) | Not exists | Need to create |
| CardType (Enum) | Exists in `infrastructure/pg/PgDto.kt` L78-82 | Need to move to domain |
| PgPaymentCreateResult (sealed) | Not exists (similar: PgPaymentResponse) | Need to create |
| PgTransaction (Value Object) | Similar: PgPaymentDetailResponse | Need domain value object |
| PgTransactionStatus (Enum) | Exists in `infrastructure/pg/PgDto.kt` L69-73 | Need to move to domain |

---

## 2. Integration Points

### 2.1 OrderFacade - PG Call Location

| Attribute | Value |
|-----------|-------|
| File | `/apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` |
| Method | `requestPayment` |
| Lines | L271-L304 |
| Current Usage | `pgClient.requestPayment(userId, PgPaymentRequest(...))` |

**Current Flow**:
1. `allocateResources()` - TX1: Stock, Coupon, Point deduction + PENDING Payment creation
2. `requestPayment()` - PG call outside transaction
3. `handlePgSuccess/handlePgFailure` - TX2: Result processing

**Modification Point**: `requestPayment()` method needs to use domain PgClient instead of infrastructure PgClient

### 2.2 PaymentFacade - PG Query Location

| Attribute | Value |
|-----------|-------|
| File | `/apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentFacade.kt` |
| Method | `queryPgStatus` |
| Lines | L144-L175 |
| Current Usage | `pgClient.getPaymentsByOrderId(userId, orderId)` |

**Current Flow**:
1. `findInProgressPayments()` - Get IN_PROGRESS payments
2. `queryPgStatus()` - Query PG for transaction status
3. Handle result based on `PgQueryResult` sealed class

---

## 3. Patterns to Follow

### 3.1 Interface Pattern in Domain Layer

**Reference**: `/apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/DiscountPolicy.kt`

```kotlin
interface DiscountPolicy {
    fun supports(coupon: Coupon): Boolean
    fun calculate(orderAmount: Money, coupon: Coupon): Money
}
```

**Repository Interface Pattern**: `/apps/commerce-api/src/main/kotlin/com/loopers/domain/point/PointAccountRepository.kt`

```kotlin
interface PointAccountRepository {
    fun findByUserId(userId: Long): PointAccount?
    fun findByUserIdWithLock(userId: Long): PointAccount?
    fun save(pointAccount: PointAccount): PointAccount
}
```

### 3.2 Sealed Class Pattern

**Reference 1 - Exception**: `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgException.kt` (L6-L45)

```kotlin
sealed class PgException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class RequestNotReached(message: String, cause: Throwable? = null) : PgException(message, cause)
    class ResponseUncertain(message: String, cause: Throwable? = null) : PgException(message, cause)
    class CircuitOpen(message: String) : PgException(message)
    class BusinessError(val errorCode: String, message: String) : PgException(message)
}
```

**Reference 2 - Result**: `/apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentFacade.kt` (L39-L45)

```kotlin
sealed class PgQueryResult {
    data class Success(val transactionKey: String) : PgQueryResult()
    data class Failed(val reason: String) : PgQueryResult()
    data object Pending : PgQueryResult()
    data object NotFound : PgQueryResult()
    data class QueryFailed(val reason: String) : PgQueryResult()
}
```

**Reference 3 - Repository Result**: `/apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeRepository.kt` (L9-L17)

```kotlin
sealed interface SaveResult {
    data object Created : SaveResult
    data object AlreadyExists : SaveResult
}
```

### 3.3 Value Object Pattern

**Reference 1 - Embeddable**: `/apps/commerce-api/src/main/kotlin/com/loopers/support/values/Money.kt`

- Uses `@Embeddable` annotation for JPA
- Has `data class` for value equality
- Includes validation in companion object factory methods
- Operator overloading for domain operations

**Reference 2 - Domain Value Object with Validation**: `/apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/DiscountAmount.kt`

```kotlin
@Embeddable
data class DiscountAmount(
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    val type: DiscountType,
    @Column(name = "discount_value", nullable = false)
    val value: Long,
) {
    init {
        if (value <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0보다 커야 합니다")
        }
        // ... more validation
    }
}
```

### 3.4 Enum Pattern

**Reference**: `/apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/DiscountType.kt`

```kotlin
enum class DiscountType {
    FIXED_AMOUNT,
    RATE,
}
```

**PG-Simulator Reference**: `/apps/pg-simulator/src/main/kotlin/com/loopers/domain/payment/CardType.kt`

```kotlin
enum class CardType {
    SAMSUNG,
    KB,
    HYUNDAI,
}
```

---

## 4. Where Domain Types Should Be Placed

### 4.1 Recommended Package Structure

Based on the spec and existing patterns, domain types should be in:

```
apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/
    PgClient.kt              # Interface
    PgPaymentRequest.kt      # Value Object
    CardInfo.kt              # Value Object
    CardType.kt              # Enum
    PgPaymentCreateResult.kt # Sealed class
    PgTransaction.kt         # Value Object
    PgTransactionStatus.kt   # Enum
```

**Alternative**: Place in `domain/order/pg/` if PG is considered a subdomain of Order.

### 4.2 Infrastructure Adapter Pattern

The existing `PgClientImpl` would need to:
1. Implement the new domain `PgClient` interface
2. Convert between domain types and infrastructure DTOs
3. Keep existing Resilience4j integration

---

## 5. Current ErrorType Values

**Location**: `/apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt`

```kotlin
enum class ErrorType(val status: HttpStatus, val code: String, val message: String) {
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, ...),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, ...),
    NOT_FOUND(HttpStatus.NOT_FOUND, ...),
    CONFLICT(HttpStatus.CONFLICT, ...),

    // PG related
    CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN", ...),
    PAYMENT_IN_PROGRESS(HttpStatus.ACCEPTED, "PAYMENT_IN_PROGRESS", ...),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED", ...),
}
```

**New types likely needed**:
- `INVALID_CARD_NUMBER` - CardInfo validation failure
- `UNSUPPORTED_CARD_TYPE` - CardType validation failure

---

## 6. Considerations for Planner

### 6.1 Dependency Inversion Decision

Currently, `OrderFacade` and `PaymentFacade` directly import from `com.loopers.infrastructure.pg.*`. To follow the spec:

1. Create domain interfaces/types in `domain/pg/` package
2. Update `PgClientImpl` to implement domain interface
3. Update facades to import from domain package
4. Keep infrastructure DTOs for HTTP communication, add mapping to domain types

### 6.2 Validation Rules from Spec

**CardInfo.cardNo validation**: `xxxx-xxxx-xxxx-xxxx` format
- Regex pattern: `^\d{4}-\d{4}-\d{4}-\d{4}$`
- Should be validated in `CardInfo` value object init block

### 6.3 PgPaymentCreateResult vs PgPaymentResponse

Current `PgPaymentResponse`:
```kotlin
data class PgPaymentResponse(
    val transactionKey: String,
    val status: String,
    val reason: String?,
)
```

Spec `PgPaymentCreateResult`:
```kotlin
sealed class PgPaymentCreateResult {
    data class Accepted(val transactionKey: String)
    data object Uncertain
}
```

**Mapping needed**: `PgClientImpl` should convert `PgPaymentResponse` to `PgPaymentCreateResult`

### 6.4 Files to Modify

| File | Change |
|------|--------|
| `OrderFacade.kt` | Import domain PgClient instead of infrastructure |
| `PaymentFacade.kt` | Import domain PgClient instead of infrastructure |
| `PgClientImpl.kt` | Implement domain interface, add type conversion |

### 6.5 CardType Duplication

CardType exists in two places:
1. `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgDto.kt` L78-82
2. `/apps/pg-simulator/src/main/kotlin/com/loopers/domain/payment/CardType.kt`

Decision needed: Use domain CardType or keep both (with conversion in impl).

---

## 7. Files Examined

| File | Relevance |
|------|-----------|
| `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClient.kt` | Current PgClient interface location |
| `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgDto.kt` | Current DTOs, CardType, PgTransactionStatus |
| `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgException.kt` | Sealed class pattern for exceptions |
| `/apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClientImpl.kt` | Implementation with Resilience4j |
| `/apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` | Main PG call integration point |
| `/apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentFacade.kt` | PG query integration point |
| `/apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` | Payment entity with PG fields |
| `/apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/DiscountPolicy.kt` | Interface pattern reference |
| `/apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/DiscountAmount.kt` | Value object pattern reference |
| `/apps/commerce-api/src/main/kotlin/com/loopers/domain/like/ProductLikeRepository.kt` | Sealed interface pattern reference |
| `/apps/commerce-api/src/main/kotlin/com/loopers/support/values/Money.kt` | Value object pattern reference |
| `/apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt` | Current error types |
| `/apps/pg-simulator/src/main/kotlin/com/loopers/domain/payment/CardType.kt` | PG simulator CardType reference |
| `/apps/pg-simulator/src/main/kotlin/com/loopers/domain/payment/TransactionStatus.kt` | PG simulator status reference |
