# Implementation Plan: PaymentScheduler Refactoring

**Date**: 2025-12-06
**Objective**: Refactor PaymentStatusScheduler to follow Facade pattern - separate concerns between scheduling and business logic

---

## Goals

1. **Rename** `PaymentStatusScheduler` to `PaymentScheduler`
2. **Create** `PaymentFacade` in application layer to handle all payment-related business logic
3. **Simplify** `PaymentScheduler` to only depend on `PaymentFacade`
4. **Move** PG query logic, decision making, and transaction management to `PaymentFacade`
5. **Update** tests to reflect new structure

## Non-Goals

- Changing the scheduling frequency or threshold values
- Modifying PaymentResultHandler logic
- Changing the resilience patterns (retry, circuit breaker)
- Adding new features beyond the refactoring scope

---

## Architecture Overview

### Before Refactoring

```
PaymentStatusScheduler (Infrastructure)
    |-- PaymentService (Domain)
    |-- OrderService (Domain)
    |-- PaymentResultHandler (Application)
    |-- PgClient (Infrastructure)
    |-- TransactionTemplate
```

### After Refactoring

```
PaymentScheduler (Infrastructure)
    |-- PaymentFacade (Application)

PaymentFacade (Application)
    |-- PaymentService (Domain)
    |-- OrderService (Domain)
    |-- PaymentResultHandler (Application)
    |-- PgClient (Infrastructure)
    |-- TransactionTemplate
```

---

## Milestone 1: Create PaymentFacade

### TODO

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentFacade.kt` - new Facade for payment operations (pattern: `application/order/OrderFacade.kt:L32-L72`)
- [ ] Add `PgQueryResult` sealed class in `PaymentFacade.kt` - move from PaymentStatusScheduler (current location: `infrastructure/payment/PaymentStatusScheduler.kt:L214-L220`)
- [ ] Add `findInProgressPayments(threshold: ZonedDateTime)` method in `PaymentFacade.kt` - delegate to PaymentService (pattern: `application/order/OrderFacade.kt:L358-L374`)
- [ ] Add `processInProgressPayment(payment: Payment)` method in `PaymentFacade.kt` - contains PG query, decision logic, and transaction handling (spec: 상세_설계_문서_PG연동_Resilience.md#1.3 스케줄러 상태 확정 플로우)
- [ ] Add `queryPgStatus(userId: Long, orderId: String)` private method in `PaymentFacade.kt` - PG status query logic (move from: `infrastructure/payment/PaymentStatusScheduler.kt:L170-L199`)
- [ ] Add `getOrderItems(orderId: Long)` private method in `PaymentFacade.kt` - order items lookup for recovery (move from: `infrastructure/payment/PaymentStatusScheduler.kt:L201-L209`)

### Implementation Details

**PaymentFacade Structure:**
```kotlin
@Component
class PaymentFacade(
    private val paymentService: PaymentService,
    private val orderService: OrderService,
    private val paymentResultHandler: PaymentResultHandler,
    private val pgClient: PgClient,
    private val transactionTemplate: TransactionTemplate,
) {
    companion object {
        const val FORCE_FAIL_THRESHOLD_MINUTES = 5L
    }

    // PG query result - same as current PaymentStatusScheduler.PgQueryResult
    sealed class PgQueryResult {
        data class Success(val transactionKey: String) : PgQueryResult()
        data class Failed(val reason: String) : PgQueryResult()
        data object Pending : PgQueryResult()
        data object NotFound : PgQueryResult()
        data class QueryFailed(val reason: String) : PgQueryResult()
    }

    fun findInProgressPayments(threshold: ZonedDateTime): List<Payment>
    fun processInProgressPayment(payment: Payment)
    private fun queryPgStatus(userId: Long, orderId: String): PgQueryResult
    private fun getOrderItems(orderId: Long): List<PaymentResultHandler.OrderItemInfo>
}
```

**processInProgressPayment Logic:**
- Query PG status outside transaction (using `queryPgStatus`)
- Handle result inside transaction using `transactionTemplate.execute`:
  - Success: call `paymentResultHandler.handlePaymentSuccess`
  - Failed: call `paymentResultHandler.handlePaymentFailure` with orderItems
  - Pending: check if 5+ minutes elapsed, force fail if so
  - NotFound: treat as failure (PG request didn't reach)
  - QueryFailed: do nothing (retry next schedule)

### Tests

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/application/payment/PaymentFacadeTest.kt` - unit tests for PaymentFacade (pattern: `infrastructure/payment/PaymentStatusSchedulerTest.kt:L31-L64`)
    - Cover: PG SUCCESS handling, PG FAILED handling, PENDING within threshold, PENDING over 5min force fail, NOT_FOUND handling, query failure handling

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentFacadeTest"` passes
- [ ] PaymentFacade.kt compiles without errors
- [ ] All 5 PgQueryResult cases have corresponding test methods

---

## Milestone 2: Refactor PaymentScheduler

### TODO

- [ ] Rename `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentStatusScheduler.kt` to `PaymentScheduler.kt`
- [ ] Modify `PaymentScheduler.kt` constructor - replace all dependencies with single `PaymentFacade` dependency
- [ ] Modify `checkInProgressPayments()` method in `PaymentScheduler.kt` - simplify to iterate and delegate to Facade (current location: L43-L85)
- [ ] Remove `processPayment()` method from `PaymentScheduler.kt` - logic moved to Facade (current location: L87-L168)
- [ ] Remove `queryPgStatus()` method from `PaymentScheduler.kt` - logic moved to Facade (current location: L170-L199)
- [ ] Remove `getOrderItems()` method from `PaymentScheduler.kt` - logic moved to Facade (current location: L201-L209)
- [ ] Remove `PgQueryResult` sealed class from `PaymentScheduler.kt` - moved to Facade (current location: L214-L220)
- [ ] Keep `CHECK_THRESHOLD_MINUTES` constant in `PaymentScheduler.kt` - scheduler-specific config (current location: L35)
- [ ] Move `FORCE_FAIL_THRESHOLD_MINUTES` to `PaymentFacade.kt` - business logic config (current location: L36)

### Implementation Details

**New PaymentScheduler Structure:**
```kotlin
@Component
class PaymentScheduler(
    private val paymentFacade: PaymentFacade,
) {
    companion object {
        private const val CHECK_THRESHOLD_MINUTES = 1L
    }

    @Scheduled(fixedRate = 60_000)
    fun checkInProgressPayments() {
        val threshold = ZonedDateTime.now().minusMinutes(CHECK_THRESHOLD_MINUTES)
        val inProgressPayments = paymentFacade.findInProgressPayments(threshold)

        if (inProgressPayments.isEmpty()) return

        logger.info("IN_PROGRESS 결제 상태 확인 시작 - count: {}", inProgressPayments.size)

        var processedCount = 0
        var skippedCount = 0

        for (payment in inProgressPayments) {
            try {
                paymentFacade.processInProgressPayment(payment)
                processedCount++
            } catch (e: ObjectOptimisticLockingFailureException) {
                logger.debug("낙관적 락 충돌 - paymentId: {}", payment.id)
                skippedCount++
            } catch (e: Exception) {
                logger.error("결제 상태 확인 오류 - paymentId: {}", payment.id, e)
            }
        }

        logger.info("IN_PROGRESS 결제 상태 확인 완료 - processed: {}, skipped: {}", processedCount, skippedCount)
    }
}
```

### Tests

- [ ] Rename `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/payment/PaymentStatusSchedulerTest.kt` to `PaymentSchedulerTest.kt`
- [ ] Modify `PaymentSchedulerTest.kt` - update to mock only `PaymentFacade` instead of individual services
- [ ] Simplify test cases in `PaymentSchedulerTest.kt` - test scheduler iteration/exception handling, not business logic (business logic tested in PaymentFacadeTest)

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentSchedulerTest"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentFacadeTest"` passes
- [ ] PaymentScheduler.kt only has one dependency: PaymentFacade
- [ ] No compile errors in the project
- [ ] `grep -r "PaymentStatusScheduler" apps/commerce-api/src/` returns empty (old class name removed)

---

## Milestone 3: Integration Verification

### TODO

- [ ] Run full test suite to verify no regressions
- [ ] Verify PaymentWebhookController still works (uses OrderFacade.handlePaymentResult, not affected)
- [ ] Verify OrderFacade still works (independent of PaymentFacade)

### Tests

- [ ] Run `./gradlew :apps:commerce-api:test` - all tests pass
- [ ] Run `./gradlew :apps:commerce-api:test --tests "*PaymentSchedulerCriticalScenarioTest"` - if exists, verify critical scenarios still pass

### Done When

- [ ] `./gradlew :apps:commerce-api:test` passes (all tests green)
- [ ] `./gradlew :apps:commerce-api:build` succeeds
- [ ] No ktlint errors: `./gradlew ktlintCheck` passes

---

## File Changes Summary

| Action | File Path | Description |
|--------|-----------|-------------|
| CREATE | `apps/commerce-api/src/main/kotlin/com/loopers/application/payment/PaymentFacade.kt` | New Facade with PG query logic |
| CREATE | `apps/commerce-api/src/test/kotlin/com/loopers/application/payment/PaymentFacadeTest.kt` | Unit tests for PaymentFacade |
| RENAME | `...infrastructure/payment/PaymentStatusScheduler.kt` -> `PaymentScheduler.kt` | Rename class |
| MODIFY | `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentScheduler.kt` | Simplify to use only PaymentFacade |
| RENAME | `...infrastructure/payment/PaymentStatusSchedulerTest.kt` -> `PaymentSchedulerTest.kt` | Rename test class |
| MODIFY | `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/payment/PaymentSchedulerTest.kt` | Update to mock PaymentFacade |

---

## Pattern References

| Pattern | Reference File | Lines |
|---------|---------------|-------|
| Facade with TransactionTemplate | `application/order/OrderFacade.kt` | L32-L72, L313-L315 |
| Facade with PgClient | `application/order/OrderFacade.kt` | L271-L304 |
| Simple Facade | `application/like/LikeFacade.kt` | L8-L30 |
| PaymentResultHandler usage | `application/order/PaymentResultHandler.kt` | L38-L46, L60-L86 |
| PgQueryResult pattern | `infrastructure/payment/PaymentStatusScheduler.kt` | L214-L220 |
| Scheduler exception handling | `infrastructure/payment/PaymentStatusScheduler.kt` | L60-L77 |

---

## Spec Requirement Mapping

| Requirement | Spec Reference | Milestone |
|-------------|---------------|-----------|
| Scheduler processes IN_PROGRESS payments | 상세_설계_문서_PG연동_Resilience.md#1.3 | M1, M2 |
| PG query and status interpretation | 상세_설계_문서_PG연동_Resilience.md#3.4 | M1 |
| 5-minute force failure | 상세_설계_문서_PG연동_Resilience.md#3.4 | M1 |
| Optimistic lock handling | 상세_설계_문서_PG연동_Resilience.md#3.4 | M2 |
| Individual transaction per payment | 상세_설계_문서_PG연동_Resilience.md#3.4 | M1 |
| Compensation transaction | 도메인_모델링_문서_PG연동_Resilience.md#2.3.3 | M1 |

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Breaking existing scheduler behavior | Preserve all logic in PaymentFacade, only change where it lives |
| Test coverage gaps | Create comprehensive PaymentFacadeTest before modifying scheduler |
| Circular dependency | PaymentFacade is in application layer, PaymentScheduler in infrastructure - no circular dependency possible |
| Missing edge cases | Move existing test cases from PaymentStatusSchedulerTest to appropriate test class |

---

## TDD Execution Order

1. **Write PaymentFacadeTest** - Define expected behavior through tests
2. **Implement PaymentFacade** - Make tests pass
3. **Write simplified PaymentSchedulerTest** - Define new scheduler behavior
4. **Refactor PaymentScheduler** - Make tests pass
5. **Run full test suite** - Verify no regressions
