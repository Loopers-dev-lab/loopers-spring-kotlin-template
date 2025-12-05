# Implementation Plan: PG Integration Resilience

**Date**: 2025-12-06
**Feature**: PG (Payment Gateway) Integration with Circuit Breaker, Retry, Timeout patterns
**Spec Directory**: `docs/specs/`

---

## Executive Summary

This plan implements PG (Payment Gateway) integration with resilience patterns (Circuit Breaker, Retry, Timeout) for the e-commerce order system. The implementation enables card payments alongside existing point payments, with proper failure handling and recovery mechanisms.

### Goals (In Scope)

1. **Card Payment Support**: Enable card payments through external PG API
2. **Resilience Patterns**: Apply Circuit Breaker, Retry, and Timeout to PG calls
3. **Async Payment Handling**: Support callback and scheduler-based payment confirmation
4. **Resource Recovery**: Implement rollback logic for failed payments (point restore, coupon cancel, stock increase)
5. **Monitoring**: Expose metrics for PG call success/failure rates and circuit breaker state

### Non-Goals (Out of Scope)

1. Refund/cancellation flow after successful payment
2. Multiple PG provider support
3. Partial payment retry (different card for failed payment)
4. User notification system for payment status changes
5. Payment history/log admin dashboard

---

## Phase 1: Synthesis and Analysis

### Critical Thinking Summary

**Core Requirements Analysis:**

The PG integration fundamentally changes the payment flow from synchronous (point deduction = instant PAID) to asynchronous (PG call -> callback/scheduler confirmation). This requires:

1. **Transaction Splitting**: Current single transaction must be split into:
   - TX1: Resource allocation (stock, coupon, point) + Payment PENDING
   - Outside TX: PG API call with Resilience4j
   - TX2 (on failure): Resource recovery + Payment FAILED

2. **State Management**: Payment needs new states (PENDING, IN_PROGRESS) and Order needs CANCELLED state

3. **Concurrency Control**: Both callback and scheduler may process same payment simultaneously - needs optimistic lock

4. **Error Classification**: Network errors must be classified by "request reached PG or not" to decide rollback vs wait

**Assumptions Being Made:**

- PG Simulator is running on localhost (callback URL restriction)
- Single PG provider (no multi-PG routing needed)
- Optimistic lock is sufficient for callback/scheduler concurrency
- 5-minute timeout for IN_PROGRESS payments is acceptable

**Risk Assessment:**

- **Highest Risk**: OrderFacade refactoring - major flow change with transaction splitting
- **Medium Risk**: Payment state machine - new states and transitions
- **Lower Risk**: Domain entity changes (add methods) - straightforward additions

---

## Milestone Overview

| # | Milestone | Layer | Key Components |
|---|-----------|-------|----------------|
| 1 | Domain - Enums & Entity Fields | Domain | PaymentStatus, OrderStatus, Payment fields |
| 2 | Domain - State Transition Methods | Domain | Payment.start/success/fail, Order.cancel, PointAccount.restore, IssuedCoupon.cancelUse |
| 3 | Infrastructure - Repository & PgClient | Infrastructure | PaymentRepository extensions, PgClient with Resilience4j |
| 4 | Domain Service - PaymentService | Domain | PaymentService for state management |
| 5 | Application - OrderFacade Refactoring | Application | Transaction splitting, recovery logic |
| 6 | Interface - Webhook Controller | Interfaces | PaymentWebhookController for PG callbacks |
| 7 | Scheduler - Payment Status Scheduler | Infrastructure | PaymentStatusScheduler for IN_PROGRESS timeout handling |
| 8 | Critical Scenario Verification | Testing | Concurrency, idempotency, boundary tests |

---

## Milestone 1: Domain - Enums & Entity Fields

### Purpose

Extend PaymentStatus and OrderStatus enums with new values, add new fields to Payment entity for PG integration.

### TODO

- [ ] Add `PENDING`, `IN_PROGRESS`, `FAILED` to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentStatus.kt` - extend enum (spec: domain-modeling#1.2 PaymentStatus enum, pattern: `PaymentStatus.kt:L1-L6`)

- [ ] Add `CANCELLED` to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderStatus.kt` - extend enum (spec: domain-modeling#2.2 OrderStatus enum, pattern: `OrderStatus.kt:L1-L6`)

- [ ] Add `paidAmount: Money` field to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` - card payment amount (spec: domain-modeling#1.2 class diagram, pattern: `Payment.kt:L31-L48` for Money field pattern)

- [ ] Add `externalPaymentKey: String?` field to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` - PG transaction key (spec: domain-modeling#1.2 class diagram)

- [ ] Add `failureMessage: String?` field to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` - failure reason (spec: domain-modeling#1.2 class diagram)

- [ ] Add `@Version` field to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` - optimistic locking (spec: detailed-design#1.2, pattern: `IssuedCoupon.kt:L53-L56`)

- [ ] Add error types to `apps/commerce-api/src/main/kotlin/com/loopers/support/error/ErrorType.kt` - CIRCUIT_OPEN, PAYMENT_IN_PROGRESS, PAYMENT_FAILED (pattern: `ErrorType.kt:L1-L15`)

### Tests

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/PaymentTest.kt` - add tests for new field validation (pattern: `PaymentTest.kt:L11-L314`)
  - Test: Payment creation with paidAmount validation
  - Test: usedPoint + paidAmount + couponDiscount == totalAmount validation

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentTest*"` passes
- [ ] `grep -r "@Version" apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` returns Payment.kt
- [ ] PaymentStatus enum has 4 values: PENDING, IN_PROGRESS, PAID, FAILED
- [ ] OrderStatus enum has 3 values: PLACED, PAID, CANCELLED

---

## Milestone 2: Domain - State Transition Methods

### Purpose

Implement state transition methods for Payment, Order, PointAccount, and IssuedCoupon entities per domain modeling spec.

### Clarifications

- [ ] Confirm: Payment.fail() should work from both PENDING and IN_PROGRESS states per spec - verified in domain-modeling#1.3

### TODO

- [ ] Modify `Payment.pay()` factory method in `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` - rename to `pending()` and create PENDING status instead of PAID (spec: domain-modeling#1.3 creation rule, modify: `Payment.kt:L55-L104`)

- [ ] Add `start()` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` - PENDING -> IN_PROGRESS transition (spec: domain-modeling#1.3 start rule)

- [ ] Add `updateExternalPaymentKey(key: String)` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` - save PG transaction key (spec: domain-modeling#1.3 updateExternalPaymentKey rule)

- [ ] Add `success()` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` - IN_PROGRESS -> PAID transition (spec: domain-modeling#1.3 success rule)

- [ ] Add `fail(failureMessage: String?)` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Payment.kt` - PENDING/IN_PROGRESS -> FAILED transition (spec: domain-modeling#1.3 fail rule)

- [ ] Add `cancel()` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Order.kt` - PLACED -> CANCELLED transition (spec: domain-modeling#2.3 cancel rule, pattern: `Order.kt:L86-L98` for pay() pattern)

- [ ] Add `restore(amount: Money)` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/point/PointAccount.kt` - add amount to balance (spec: domain-modeling#3.3 restore rule, pattern: `PointAccount.kt:L54-L72` for deduct() pattern)

- [ ] Add `cancelUse()` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/IssuedCoupon.kt` - USED -> AVAILABLE transition (spec: domain-modeling#4.3 cancelUse rule, pattern: `IssuedCoupon.kt:L58-L81` for use() pattern)

### Tests

- [ ] Add tests to `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/PaymentTest.kt` - state transition tests
  - Test: start() from PENDING succeeds
  - Test: start() from non-PENDING throws CoreException
  - Test: success() from IN_PROGRESS succeeds
  - Test: success() from non-IN_PROGRESS throws CoreException
  - Test: fail() from PENDING succeeds
  - Test: fail() from IN_PROGRESS succeeds
  - Test: fail() from PAID/FAILED throws CoreException
  - Test: updateExternalPaymentKey() from IN_PROGRESS succeeds

- [ ] Add tests to `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderTest.kt` - cancel tests (pattern: existing OrderTest file)
  - Test: cancel() from PLACED succeeds
  - Test: cancel() from PAID throws CoreException

- [ ] Add tests to `apps/commerce-api/src/test/kotlin/com/loopers/domain/point/PointAccountTest.kt` - restore tests
  - Test: restore() increases balance
  - Test: restore() with zero/negative throws CoreException

- [ ] Add tests to `apps/commerce-api/src/test/kotlin/com/loopers/domain/coupon/IssuedCouponTest.kt` - cancelUse tests
  - Test: cancelUse() from USED succeeds and sets usedAt to null
  - Test: cancelUse() from AVAILABLE throws CoreException

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentTest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*OrderTest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*PointAccountTest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*IssuedCouponTest*"` passes
- [ ] All 5 Payment state transitions from spec 1.4 have corresponding test methods
- [ ] Order.cancel() method exists and is tested
- [ ] PointAccount.restore() method exists and is tested
- [ ] IssuedCoupon.cancelUse() method exists and is tested

---

## Milestone 3: Infrastructure - Repository & PgClient

### Purpose

Extend PaymentRepository with new query methods, create PgClient with Resilience4j integration for PG API calls.

### TODO

#### Dependencies

- [ ] Add Resilience4j dependencies to `apps/commerce-api/build.gradle.kts` - circuit breaker, retry, timelimiter (spec: solution-design#1.3 tech stack)
```
implementation("io.github.resilience4j:resilience4j-spring-boot3")
implementation("io.github.resilience4j:resilience4j-circuitbreaker")
implementation("io.github.resilience4j:resilience4j-retry")
implementation("io.github.resilience4j:resilience4j-timelimiter")
implementation("org.springframework.boot:spring-boot-starter-webflux")
```

#### Configuration

- [ ] Create `apps/commerce-api/src/main/resources/application-pg.yml` - PG configuration (spec: detailed-design#3 resilience strategy)
  - pg.base-url
  - pg.callback-url-base
  - resilience4j.circuitbreaker settings
  - resilience4j.retry settings
  - resilience4j.timelimiter settings

#### Repository Extensions

- [ ] Add `findByIdWithLock(id: Long): Payment?` to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentRepository.kt` - interface method

- [ ] Add `findInProgressPaymentIds(threshold: ZonedDateTime): List<Long>` to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentRepository.kt` - interface method for scheduler

- [ ] Add `findByExternalPaymentKey(key: String): Payment?` to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentRepository.kt` - interface method for callback lookup

- [ ] Implement `findByIdWithLock` in `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/PaymentJpaRepository.kt` - pessimistic lock (pattern: `PointAccountJpaRepository.kt:L13-L15`)

- [ ] Implement `findInProgressPaymentIds` in `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/PaymentJpaRepository.kt` - query IN_PROGRESS payments older than threshold

- [ ] Implement `findByExternalPaymentKey` in `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/PaymentJpaRepository.kt` - query by external key

- [ ] Update `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/order/PaymentRdbRepository.kt` - implement new interface methods (pattern: `PointAccountRdbRepository.kt`)

#### PG Client DTOs

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/dto/PgPaymentRequest.kt` - PG request DTO (spec: PG_SIMULATOR_API.md request body)

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/dto/PgPaymentResponse.kt` - PG response DTO (spec: PG_SIMULATOR_API.md response format)

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/dto/PgTransactionInfo.kt` - transaction info DTO (spec: PG_SIMULATOR_API.md transaction status)

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/dto/PgTransactionListResponse.kt` - list response DTO (spec: PG_SIMULATOR_API.md#3 order payments list)

#### PG Client Implementation

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PgClient.kt` - PG client interface with methods:
  - `requestPayment(orderId: String, cardType: String, cardNo: String, amount: Long, callbackUrl: String): PgPaymentResponse`
  - `getPaymentByTransactionKey(transactionKey: String): PgTransactionInfo`
  - `getPaymentsByOrderId(orderId: String): PgTransactionListResponse`

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PgClientImpl.kt` - implementation with Resilience4j (pattern: `PaymentCoreRelay.kt` for RestTemplate usage)
  - Apply CircuitBreaker with settings from spec detailed-design#3.3
  - Apply Retry with settings from spec detailed-design#3.2
  - Apply Timeout with settings from spec detailed-design#3.1
  - Record metrics using Micrometer (spec: detailed-design#4.1)

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/exception/PgException.kt` - PG-specific exceptions
  - `PgRequestNotReachedException` - circuit open, connection timeout, 500/429
  - `PgResponseUncertainException` - read timeout, connection reset

### Tests

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/order/PaymentRdbRepositoryIntegrationTest.kt` - repository integration tests
  - Test: findByIdWithLock returns Payment with lock
  - Test: findInProgressPaymentIds returns correct IDs
  - Test: findByExternalPaymentKey returns correct Payment

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/payment/PgClientImplTest.kt` - PG client unit tests with mocked responses
  - Test: requestPayment success case
  - Test: requestPayment with circuit open throws PgRequestNotReachedException
  - Test: requestPayment with timeout throws PgResponseUncertainException
  - Test: retry behavior on transient failures

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentRdbRepositoryIntegrationTest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*PgClientImplTest*"` passes
- [ ] `./gradlew :apps:commerce-api:dependencies | grep resilience4j` shows dependencies
- [ ] PgClient has all 3 methods implemented
- [ ] Resilience4j configuration exists in application-pg.yml

---

## Milestone 4: Domain Service - PaymentService

### Purpose

Create PaymentService to handle Payment entity state management and PG integration logic.

### TODO

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/PaymentService.kt` - payment service (pattern: `PointService.kt` for service pattern)
  - `createPending(...)` - create Payment in PENDING state
  - `startPayment(paymentId: Long)` - transition to IN_PROGRESS
  - `completePayment(paymentId: Long)` - transition to PAID
  - `failPayment(paymentId: Long, message: String?)` - transition to FAILED
  - `findInProgressPayments(threshold: ZonedDateTime): List<Payment>` - for scheduler
  - `findByExternalPaymentKey(key: String): Payment?` - for callback

- [ ] Add `restorePoint(userId: Long, amount: Money)` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/point/PointService.kt` - restore point on payment failure (pattern: `PointService.kt:L20-L30` for deduct pattern)

- [ ] Add `cancelCouponUse(issuedCouponId: Long)` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/coupon/CouponService.kt` - cancel coupon usage (pattern: existing CouponService methods)

- [ ] Add `increaseStocks(items: List<StockIncreaseItem>)` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt` - restore stock on payment failure (spec: domain-modeling#5.1, pattern: `ProductService.kt` decreaseStocks method)

- [ ] Add `cancelOrder(orderId: Long)` method to `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderService.kt` - cancel order on payment failure (pattern: existing OrderService methods)

### Tests

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/PaymentServiceIntegrationTest.kt` - integration tests
  - Test: createPending creates Payment in PENDING state
  - Test: startPayment transitions PENDING -> IN_PROGRESS
  - Test: completePayment transitions IN_PROGRESS -> PAID
  - Test: failPayment transitions to FAILED
  - Test: findInProgressPayments returns correct payments

- [ ] Add tests to `apps/commerce-api/src/test/kotlin/com/loopers/domain/point/PointServiceIntegrationTest.kt` - restore tests
  - Test: restorePoint increases balance

- [ ] Add tests to `apps/commerce-api/src/test/kotlin/com/loopers/domain/coupon/CouponServiceIntegrationTest.kt` - cancel coupon tests
  - Test: cancelCouponUse reverts coupon to AVAILABLE

- [ ] Add tests to `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt` - stock increase tests
  - Test: increaseStocks increases stock quantity

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentServiceIntegrationTest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*PointServiceIntegrationTest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*CouponServiceIntegrationTest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*ProductServiceIntegrationTest*"` passes
- [ ] PaymentService has all 6 methods implemented
- [ ] Recovery methods (restore, cancel, increase) are implemented in respective services

---

## Milestone 5: Application - OrderFacade Refactoring

### Purpose

Refactor OrderFacade.placeOrder() to support two-transaction flow with PG integration and recovery logic.

### Clarifications

- [ ] Confirm: Card payment info (cardType, cardNo) should be added to OrderCriteria.PlaceOrder

### TODO

#### Criteria/Info Updates

- [ ] Add `cardType: String?` to `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCriteria.kt` - PlaceOrder data class

- [ ] Add `cardNo: String?` to `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderCriteria.kt` - PlaceOrder data class

- [ ] Add `paymentStatus: PaymentStatus` to `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderInfo.kt` - PlaceOrder response

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/application/order/PaymentResultHandler.kt` - handles payment result processing
  - `handlePaymentSuccess(paymentId: Long)` - complete payment and order
  - `handlePaymentFailure(paymentId: Long, reason: String?)` - fail payment, recover resources, cancel order

#### OrderFacade Refactoring

- [ ] Add `PaymentService` dependency to `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` - constructor injection

- [ ] Add `PgClient` dependency to `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` - constructor injection

- [ ] Add `PaymentResultHandler` dependency to `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` - constructor injection

- [ ] Refactor `placeOrder()` in `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` - split into two transactions (spec: solution-design#3.2 transaction strategy, modify: `OrderFacade.kt:L59-L122`)
  - TX1: decreaseStocks -> useCoupon -> deductPoint -> createPendingPayment -> placeOrder -> commit
  - PG Call: PgClient.requestPayment with Resilience4j
  - TX2 (on failure): failPayment -> restorePoint -> cancelCouponUse -> increaseStocks -> cancelOrder

- [ ] Add `handlePaymentResult()` method to `apps/commerce-api/src/main/kotlin/com/loopers/application/order/OrderFacade.kt` - for callback/scheduler (spec: solution-design#3.3, #3.4)

### Tests

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/application/order/OrderFacadeIntegrationTest.kt` - integration tests for new flow
  - Test: placeOrder with card payment creates Payment in IN_PROGRESS
  - Test: PG success leads to Payment PAID
  - Test: PG request not reached (circuit open) leads to Payment FAILED and resource recovery
  - Test: PG response uncertain (timeout) leads to Payment IN_PROGRESS
  - Test: handlePaymentResult success flow
  - Test: handlePaymentResult failure flow with resource recovery
  - Test: point-only payment (no card) creates Payment PAID immediately

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*OrderFacadeIntegrationTest*"` passes
- [ ] placeOrder handles both point-only and card payment flows
- [ ] Transaction splitting is implemented per spec
- [ ] Resource recovery is implemented for payment failures
- [ ] PaymentResultHandler is called from both callback and scheduler

---

## Milestone 6: Interface - Webhook Controller

### Purpose

Create PaymentWebhookController to receive PG callback notifications and update payment status.

### TODO

#### DTOs

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/payment/dto/PaymentCallbackRequest.kt` - callback request DTO
  - transactionKey: String
  - orderId: String
  - status: String (SUCCESS/FAILED)
  - reason: String?

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/payment/dto/PaymentCallbackResponse.kt` - callback response DTO

#### Controller

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/payment/PaymentWebhookController.kt` - webhook controller (pattern: existing controllers in `interfaces/api/`)
  - `POST /api/v1/payments/callback` - receive PG callback
  - Find Payment by externalPaymentKey
  - If already processed (PAID/FAILED), return 200 OK (idempotent)
  - If IN_PROGRESS, call PaymentResultHandler

#### Order API Updates

- [ ] Add payment status to order detail response in `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/order/OrderV1Api.kt` - include payment status in response (spec: requirements#3.4 UC-2)

### Tests

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/payment/PaymentWebhookControllerE2ETest.kt` - E2E tests (pattern: `OrderV1ApiE2ETest.kt`)
  - Test: callback with SUCCESS updates Payment to PAID
  - Test: callback with FAILED updates Payment to FAILED and recovers resources
  - Test: callback for already processed payment returns 200 OK
  - Test: callback for non-existent transactionKey returns appropriate error

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/order/OrderV1ApiE2ETest.kt` - order detail includes payment status

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentWebhookControllerE2ETest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*OrderV1ApiE2ETest*"` passes
- [ ] POST /api/v1/payments/callback endpoint exists
- [ ] Callback is idempotent (duplicate requests handled gracefully)
- [ ] Order detail API includes payment status

---

## Milestone 7: Scheduler - Payment Status Scheduler

### Purpose

Create PaymentStatusScheduler to check and finalize IN_PROGRESS payments that haven't received callbacks.

### TODO

- [ ] Add scheduling configuration to `apps/commerce-api/src/main/kotlin/com/loopers/CommerceApiApplication.kt` - @EnableScheduling annotation

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/payment/PaymentStatusScheduler.kt` - scheduler (spec: detailed-design#3.4 scheduler strategy)
  - Run every 1 minute
  - Query IN_PROGRESS payments older than 1 minute
  - For each payment, query PG status
  - If confirmed, call PaymentResultHandler
  - If still pending and > 5 minutes, force fail
  - Use individual transactions per payment
  - Catch OptimisticLockingFailureException and skip

- [ ] Add scheduler logging - INFO on start with count, WARN on forced failure

### Tests

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/payment/PaymentStatusSchedulerIntegrationTest.kt` - integration tests
  - Test: scheduler processes IN_PROGRESS payments
  - Test: scheduler skips already processed payments (optimistic lock)
  - Test: scheduler forces failure for 5+ minute old payments
  - Test: scheduler handles PG query failure gracefully

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentStatusSchedulerIntegrationTest*"` passes
- [ ] Scheduler runs every 1 minute
- [ ] IN_PROGRESS payments older than 5 minutes are force-failed
- [ ] Optimistic lock conflicts are handled gracefully

---

## Milestone 8: Critical Scenario Verification

### Purpose

Verify system behavior under concurrent access, edge cases, and failure scenarios.

### TODO

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/integration/PaymentConcurrencyTest.kt` - concurrency tests
  - Test: simultaneous callback and scheduler processing same payment
  - Test: only one succeeds, other gets OptimisticLockingFailureException

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/integration/PaymentIdempotencyTest.kt` - idempotency tests
  - Test: duplicate callback requests are handled idempotently
  - Test: scheduler + callback for same payment is handled correctly

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/integration/PaymentBoundaryTest.kt` - boundary tests
  - Test: zero card amount (point-only payment)
  - Test: zero point usage (card-only payment)
  - Test: mixed payment (point + card)
  - Test: payment with coupon discount

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/integration/PaymentRecoveryTest.kt` - recovery tests
  - Test: circuit open leads to full resource recovery
  - Test: PG rejection leads to full resource recovery
  - Test: timeout does not trigger recovery (wait for scheduler)

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentConcurrencyTest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentIdempotencyTest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentBoundaryTest*"` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*PaymentRecoveryTest*"` passes
- [ ] All critical scenarios from spec are covered

---

## Spec Requirement Mapping

| Spec Requirement | Milestone | Component |
|------------------|-----------|-----------|
| UC-1: Card Payment | M5, M6 | OrderFacade, WebhookController |
| UC-2: Order Detail with Payment Status | M6 | OrderV1Api |
| UC-3: PG Monitoring (metrics) | M3 | PgClientImpl with Micrometer |
| UC-4: Circuit Open Alert (logging) | M3 | PgClientImpl logging |
| Domain: PaymentStatus states | M1 | PaymentStatus.kt |
| Domain: Payment state transitions | M2 | Payment.kt |
| Domain: Order.cancel() | M2 | Order.kt |
| Domain: PointAccount.restore() | M2 | PointAccount.kt |
| Domain: IssuedCoupon.cancelUse() | M2 | IssuedCoupon.kt |
| Resilience: Timeout | M3 | PgClientImpl |
| Resilience: Retry | M3 | PgClientImpl |
| Resilience: Circuit Breaker | M3 | PgClientImpl |
| Transaction: Split strategy | M5 | OrderFacade |
| Callback handling | M6 | PaymentWebhookController |
| Scheduler processing | M7 | PaymentStatusScheduler |
| Concurrency: Optimistic lock | M1, M8 | Payment @Version |
| Error classification | M3 | PgException hierarchy |

---

## Database Migration (Reference)

```sql
-- Migration: Add PG integration fields to payment table
ALTER TABLE payment
    ADD COLUMN paid_amount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN external_payment_key VARCHAR(100),
    ADD COLUMN failure_message VARCHAR(500),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Index for scheduler query
CREATE INDEX idx_payment_status_updated_at ON payment (status, updated_at);

-- Unique index for callback lookup
CREATE UNIQUE INDEX idx_payment_external_payment_key ON payment (external_payment_key) WHERE external_payment_key IS NOT NULL;
```

---

## File Reference Summary

### Files to Create

| File | Purpose |
|------|---------|
| `infrastructure/payment/PgClient.kt` | PG client interface |
| `infrastructure/payment/PgClientImpl.kt` | PG client implementation with Resilience4j |
| `infrastructure/payment/PaymentStatusScheduler.kt` | IN_PROGRESS payment scheduler |
| `infrastructure/payment/dto/*.kt` | PG request/response DTOs |
| `infrastructure/payment/exception/PgException.kt` | PG-specific exceptions |
| `interfaces/api/payment/PaymentWebhookController.kt` | Webhook controller |
| `interfaces/api/payment/dto/*.kt` | Webhook DTOs |
| `application/order/PaymentResultHandler.kt` | Payment result processing |
| `resources/application-pg.yml` | PG and Resilience4j configuration |

### Files to Modify

| File | Changes |
|------|---------|
| `domain/order/Payment.kt` | Add fields, state transition methods |
| `domain/order/PaymentStatus.kt` | Add PENDING, IN_PROGRESS, FAILED |
| `domain/order/Order.kt` | Add cancel() method |
| `domain/order/OrderStatus.kt` | Add CANCELLED |
| `domain/order/PaymentRepository.kt` | Add new query methods |
| `domain/order/PaymentService.kt` | Create new service |
| `domain/point/PointAccount.kt` | Add restore() method |
| `domain/point/PointService.kt` | Add restorePoint() method |
| `domain/coupon/IssuedCoupon.kt` | Add cancelUse() method |
| `domain/coupon/CouponService.kt` | Add cancelCouponUse() method |
| `domain/product/ProductService.kt` | Add increaseStocks() method |
| `domain/order/OrderService.kt` | Add cancelOrder() method |
| `application/order/OrderFacade.kt` | Refactor placeOrder() for PG integration |
| `application/order/OrderCriteria.kt` | Add card payment fields |
| `application/order/OrderInfo.kt` | Add payment status |
| `infrastructure/order/PaymentJpaRepository.kt` | Implement new queries |
| `infrastructure/order/PaymentRdbRepository.kt` | Implement interface methods |
| `support/error/ErrorType.kt` | Add PG-related error types |
| `build.gradle.kts` | Add Resilience4j dependencies |

---

## Quality Checklist

- [x] research.md was read and findings incorporated
- [x] All spec documents were read completely
- [x] Every implementation item has exact file path
- [x] Every implementation item has pattern reference with file location
- [x] Modification items include line numbers where available
- [x] New error types listed with exact location to add
- [x] Every implementation item has spec section reference
- [x] Implementation details are guided through spec reference + pattern reference
- [x] Implementer can work with plan.md + spec documents
- [x] Each milestone has clear spec reference
- [x] Each milestone leaves codebase in working state
- [x] Critical scenarios from spec are covered in tests
