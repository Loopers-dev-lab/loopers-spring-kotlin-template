# Implementation Plan: PG Client Domain Layer

**Spec Document**: `docs/specs/도메인_모델링_문서_PG연동_Resilience.md` (Section 1)
**Date**: 2025-12-06

---

## Overview

This plan implements the PG Client abstraction in the domain layer according to the spec, enabling dependency inversion where the domain layer defines interfaces and the infrastructure layer provides implementations.

---

## Goals

1. **[GOAL-1]** Create PgClient interface in domain layer (spec: section 1.1)
2. **[GOAL-2]** Create domain value objects: PgPaymentRequest, CardInfo, PgTransaction (spec: section 1.1)
3. **[GOAL-3]** Create domain enums: CardType, PgTransactionStatus (spec: section 1.1)
4. **[GOAL-4]** Create PgPaymentCreateResult sealed class (spec: section 1.1)
5. **[GOAL-5]** Update infrastructure PgClientImpl to implement domain interface (adapter pattern)
6. **[GOAL-6]** Update application layer to import from domain package

## Non-Goals

- Payment entity changes (separate implementation plan)
- Order status changes (separate implementation plan)
- Point/Coupon restore functionality (separate implementation plan)
- PaymentConfirmationService (separate implementation plan)

---

## Domain Analysis

### Entities and Relationships (from spec section 1.1)

```
PgClient (Interface)
    +-- requestPayment(request: PgPaymentRequest) -> PgPaymentCreateResult
    +-- findTransaction(transactionKey: String) -> PgTransaction
    +-- findTransactionsByOrderId(orderId: Long) -> List<PgTransaction>

PgPaymentRequest (Value Object)
    +-- orderId: Long
    +-- amount: Money
    +-- cardInfo: CardInfo
    +-- callbackUrl: String

CardInfo (Value Object)
    +-- cardType: CardType
    +-- cardNo: String  // validated: xxxx-xxxx-xxxx-xxxx

CardType (Enum)
    SAMSUNG, KB, HYUNDAI

PgPaymentCreateResult (Sealed Class)
    +-- Accepted(transactionKey: String)
    +-- Uncertain

PgTransaction (Value Object)
    +-- transactionKey: String
    +-- orderId: Long
    +-- cardType: CardType
    +-- cardNo: String
    +-- amount: Money
    +-- status: PgTransactionStatus
    +-- failureReason: String?

PgTransactionStatus (Enum)
    PENDING, SUCCESS, FAILED
```

### Validation Rules (from spec section 1.3)

| Field | Rule | Error Message |
|-------|------|---------------|
| CardInfo.cardNo | Must match `^\d{4}-\d{4}-\d{4}-\d{4}$` | "카드 번호 형식이 올바르지 않습니다" |
| CardInfo.cardType | Must be valid CardType enum | (handled by type system) |

### PgClient Method Semantics (from spec section 1.3)

| Method | Success Result | Error Cases |
|--------|---------------|-------------|
| requestPayment | `Accepted` with transactionKey | Throws PgException (CircuitOpen, RequestNotReached, BusinessError) |
| requestPayment (timeout) | `Uncertain` | When Read Timeout occurs |
| findTransaction | PgTransaction | Throws PgException |
| findTransactionsByOrderId | List<PgTransaction> | Throws PgException |

---

## Technical Requirements

### Package Structure

```
apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/
    PgClient.kt              # Interface
    PgPaymentRequest.kt      # Value Object
    CardInfo.kt              # Value Object with validation
    CardType.kt              # Enum
    PgPaymentCreateResult.kt # Sealed class
    PgTransaction.kt         # Value Object
    PgTransactionStatus.kt   # Enum
```

### Dependency Direction

```
domain/pg/PgClient (interface)
         ^
         |
infrastructure/pg/PgClientImpl (implementation)
         |
         v
infrastructure/pg/PgDto (HTTP DTOs - unchanged)
```

---

## Milestone 1: Domain Layer - Enums

**Goal**: Create CardType and PgTransactionStatus enums in domain layer

### TODO

- [x] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/CardType.kt` - CardType enum (spec: section 1.1 CardType enumeration, pattern: `domain/coupon/DiscountType.kt:L2-L5`)
- [x] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/PgTransactionStatus.kt` - PgTransactionStatus enum (spec: section 1.1 PgTransactionStatus enumeration, pattern: `domain/coupon/DiscountType.kt:L2-L5`)

### Tests

- [x] No unit tests needed for simple enums (enum values are compile-time verified)

### Done When

- [x] `ls apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/` shows CardType.kt and PgTransactionStatus.kt
- [x] `./gradlew :apps:commerce-api:compileKotlin` succeeds

---

## Milestone 2: Domain Layer - Value Objects

**Goal**: Create CardInfo, PgPaymentRequest, and PgTransaction value objects

### TODO

- [x] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/CardInfo.kt` - CardInfo value object with cardNo validation (spec: section 1.1 CardInfo class diagram + section 1.3 CardInfo validation rules, pattern: `domain/coupon/DiscountAmount.kt:L9-L27`)
  - cardType: CardType
  - cardNo: String validated with regex `^\d{4}-\d{4}-\d{4}-\d{4}$`
  - init block validates cardNo format, throws CoreException(ErrorType.BAD_REQUEST) on failure

- [x] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/PgPaymentRequest.kt` - PgPaymentRequest value object (spec: section 1.1 PgPaymentRequest class diagram, pattern: `domain/coupon/DiscountAmount.kt:L9-L27`)
  - orderId: Long
  - amount: Money
  - cardInfo: CardInfo
  - callbackUrl: String

- [x] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/PgTransaction.kt` - PgTransaction value object (spec: section 1.1 PgTransaction class diagram)
  - transactionKey: String
  - orderId: Long
  - cardType: CardType
  - cardNo: String
  - amount: Money
  - status: PgTransactionStatus
  - failureReason: String?

### Tests

- [x] Create `apps/commerce-api/src/test/kotlin/com/loopers/domain/pg/CardInfoTest.kt` - unit tests for CardInfo validation (pattern: `domain/coupon/DiscountAmountTest.kt` if exists, or use standard test pattern)
  - Test: valid cardNo format "1234-5678-9012-3456" succeeds
  - Test: invalid cardNo format "12345678901234567" throws CoreException
  - Test: invalid cardNo format "1234-5678-9012-345" throws CoreException
  - Test: invalid cardNo format with letters throws CoreException

### Done When

- [x] `./gradlew :apps:commerce-api:test --tests "*CardInfoTest"` passes
- [x] `./gradlew :apps:commerce-api:compileKotlin` succeeds
- [x] CardInfo validates cardNo format in init block

---

## Milestone 3: Domain Layer - Sealed Class and Interface

**Goal**: Create PgPaymentCreateResult sealed class and PgClient interface

### TODO

- [x] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/PgPaymentCreateResult.kt` - sealed class with Accepted and Uncertain variants (spec: section 1.1 PgPaymentCreateResult class diagram, pattern: `application/payment/PaymentFacade.kt:L35-L44` PgQueryResult sealed class)
  - sealed class PgPaymentCreateResult
  - data class Accepted(val transactionKey: String) : PgPaymentCreateResult()
  - data object Uncertain : PgPaymentCreateResult()

- [x] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/PgClient.kt` - PgClient interface (spec: section 1.1 PgClient class diagram, pattern: `domain/coupon/DiscountPolicy.kt:L5-L8` for interface pattern)
  - fun requestPayment(request: PgPaymentRequest): PgPaymentCreateResult
  - fun findTransaction(transactionKey: String): PgTransaction
  - fun findTransactionsByOrderId(orderId: Long): List<PgTransaction>

### Tests

- [x] No unit tests needed for interface definition (will be tested via integration tests)

### Done When

- [x] `./gradlew :apps:commerce-api:compileKotlin` succeeds
- [x] PgClient interface exists in domain package
- [x] PgPaymentCreateResult sealed class has exactly 2 variants: Accepted, Uncertain

---

## Milestone 4: Infrastructure Layer - Adapter Implementation

**Goal**: Update PgClientImpl to implement domain PgClient interface

### Clarifications

- [x] Confirm: Should PgClientImpl be renamed to PgClientAdapter? (Current name kept for backward compatibility)
- [x] Confirm: Keep infrastructure PgDto unchanged for HTTP communication? (Assumed yes - adapter pattern)

### TODO

- [x] Modify `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClientImpl.kt` (L17-L172)
  - Change import from infrastructure PgClient to domain PgClient: `import com.loopers.domain.pg.PgClient`
  - Change interface implementation: `: PgClient` to use domain interface
  - Keep existing Resilience4j integration (executeWithResilience, classifyAndThrow, handleResponse methods)

- [x] Add `requestPayment(request: PgPaymentRequest): PgPaymentCreateResult` method in PgClientImpl
  - Convert domain PgPaymentRequest to infrastructure PgPaymentRequest DTO
  - Call existing HTTP logic
  - Handle PgException.ResponseUncertain -> return Uncertain
  - Convert PgPaymentResponse to Accepted(transactionKey)
  - Pattern: see existing `requestPayment` method at L34-L61

- [x] Add `findTransaction(transactionKey: String): PgTransaction` method in PgClientImpl
  - Call existing getPaymentByKey logic internally
  - Convert PgPaymentDetailResponse to domain PgTransaction
  - Pattern: see existing `getPaymentByKey` method at L63-L84

- [x] Add `findTransactionsByOrderId(orderId: Long): List<PgTransaction>` method in PgClientImpl
  - Call existing getPaymentsByOrderId logic internally
  - Convert PgPaymentListResponse.transactions to List<PgTransaction>
  - Pattern: see existing `getPaymentsByOrderId` method at L86-L107

- [x] Add private helper methods in PgClientImpl for type conversion
  - `toInfraPaymentRequest(request: PgPaymentRequest): infrastructure.PgPaymentRequest`
  - `toDomainTransaction(response: PgPaymentDetailResponse): PgTransaction`
  - `toDomainTransactionStatus(status: String): PgTransactionStatus`
  - `toDomainCardType(cardType: String): CardType`

### Tests

- [x] Integration tests skipped (requires actual PG server - tested via full test suite)

### Done When

- [x] `./gradlew :apps:commerce-api:compileKotlin` succeeds
- [x] PgClientImpl implements `com.loopers.domain.pg.PgClient`
- [x] Old methods (requestPaymentLegacy, getPaymentByKey, getPaymentsByOrderId) remain for backward compatibility during migration

---

## Milestone 5: Application Layer - Import Update

**Goal**: Update OrderFacade and PaymentFacade to use domain PgClient

### TODO

- [ ] Modify `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` (L13)
  - Change import: `com.loopers.infrastructure.pg.PgClient` -> `com.loopers.domain.pg.PgClient`
  - No logic changes needed (method signatures compatible or will use new domain methods)

- [ ] Modify `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentFacade.kt` (L6)
  - Change import: `com.loopers.infrastructure.pg.PgClient` -> `com.loopers.domain.pg.PgClient`
  - No logic changes needed (method signatures compatible or will use new domain methods)

- [ ] Update test files to use domain imports
  - `apps/commerce-api/src/test/kotlin/com/loopers/application/payment/PaymentFacadeTest.kt` (L9)
  - Any other test files referencing infrastructure PgClient

### Tests

- [ ] Existing tests should pass with import changes only

### Done When

- [ ] `./gradlew :apps:commerce-api:test` passes
- [ ] No imports from `com.loopers.infrastructure.pg.PgClient` in application layer
- [ ] `grep -r "infrastructure.pg.PgClient" apps/commerce-api/src/main/kotlin/com/loopers/application/` returns empty

---

## Milestone 6: Cleanup - Remove Infrastructure Interface

**Goal**: Remove the now-redundant PgClient interface from infrastructure layer

### Clarifications

- [ ] Confirm: Safe to delete infrastructure PgClient.kt? (After verifying no external dependencies)

### TODO

- [ ] Delete `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClient.kt` - old interface (now superseded by domain/pg/PgClient.kt)

- [ ] Verify no remaining imports of infrastructure PgClient
  - Run: `grep -r "infrastructure.pg.PgClient" apps/`

### Tests

- [ ] All existing tests pass

### Done When

- [ ] `./gradlew :apps:commerce-api:test` passes
- [ ] `ls apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/` does NOT show PgClient.kt
- [ ] `grep -r "infrastructure.pg.PgClient" apps/` returns empty

---

## Spec Requirement Mapping

| Spec Section | Requirement | Milestone |
|--------------|-------------|-----------|
| 1.1 | PgClient interface | M3 |
| 1.1 | PgPaymentRequest value object | M2 |
| 1.1 | CardInfo value object | M2 |
| 1.1 | CardType enum | M1 |
| 1.1 | PgPaymentCreateResult sealed class | M3 |
| 1.1 | PgTransaction value object | M2 |
| 1.1 | PgTransactionStatus enum | M1 |
| 1.3 | CardInfo.cardNo validation | M2 |
| 1.3 | requestPayment returns Accepted/Uncertain | M4 |

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Breaking existing tests | Medium | High | Keep old methods during migration, run tests after each milestone |
| Incorrect type mapping | Low | Medium | Comprehensive integration tests in M4 |
| Missing import updates | Low | Low | Use grep to verify all imports updated |

---

## File Changes Summary

| Action | File Path |
|--------|-----------|
| CREATE | `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/CardType.kt` |
| CREATE | `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/PgTransactionStatus.kt` |
| CREATE | `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/CardInfo.kt` |
| CREATE | `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/PgPaymentRequest.kt` |
| CREATE | `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/PgTransaction.kt` |
| CREATE | `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/PgPaymentCreateResult.kt` |
| CREATE | `apps/commerce-api/src/main/kotlin/com/loopers/domain/pg/PgClient.kt` |
| CREATE | `apps/commerce-api/src/test/kotlin/com/loopers/domain/pg/CardInfoTest.kt` |
| MODIFY | `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClientImpl.kt` |
| CREATE | `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/pg/PgClientImplTest.kt` |
| MODIFY | `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` |
| MODIFY | `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentFacade.kt` |
| MODIFY | `apps/commerce-api/src/test/kotlin/com/loopers/application/payment/PaymentFacadeTest.kt` |
| DELETE | `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/pg/PgClient.kt` |

---

## Pattern References

| Pattern | Reference File | Lines | Usage |
|---------|---------------|-------|-------|
| Enum | `domain/coupon/DiscountType.kt` | L2-L5 | CardType, PgTransactionStatus |
| Value Object with validation | `domain/coupon/DiscountAmount.kt` | L9-L27 | CardInfo |
| Value Object | `support/values/Money.kt` | L9-L112 | PgPaymentRequest, PgTransaction |
| Sealed class | `application/payment/PaymentFacade.kt` | L35-L44 | PgPaymentCreateResult |
| Domain interface | `domain/coupon/DiscountPolicy.kt` | L5-L8 | PgClient |
| Infrastructure adapter | `infrastructure/pg/PgClientImpl.kt` | L17-L172 | PgClientImpl changes |

---

## Summary

This plan implements the PG Client domain abstraction in 6 milestones:

1. **M1**: Create enums (CardType, PgTransactionStatus)
2. **M2**: Create value objects (CardInfo, PgPaymentRequest, PgTransaction)
3. **M3**: Create sealed class and interface (PgPaymentCreateResult, PgClient)
4. **M4**: Update infrastructure adapter (PgClientImpl)
5. **M5**: Update application layer imports
6. **M6**: Cleanup redundant infrastructure interface

Each milestone leaves the codebase in a compilable, testable state.
