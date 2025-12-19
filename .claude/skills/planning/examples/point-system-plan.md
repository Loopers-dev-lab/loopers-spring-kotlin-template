# Implementation Plan: Point Partial Usage with History

**Created**: 2025-01-15
**Specs**: docs/specs/point-spec.md
**Research**: research.md

---

## Milestones Overview

| # | Title | Scope | Risk | Dependencies |
|---|-------|-------|------|--------------|
| 1 | Point balance inquiry | Add balance query method | 🟢 Low | None |
| 2 | Point partial usage | Change full→partial deduction | 🟠 High | Milestone 1 |
| 3 | PointUsageHistory entity | Define history entity | 🟢 Low | None |
| 4 | PointUsageHistory persistence | Add repository layer | 🟢 Low | Milestone 3 |
| 5 | Usage history integration | Connect history recording | 🟡 Medium | Milestone 2, 4 |

---

## Milestone Details

- [ ] Milestone 1: Point balance inquiry

### Risk

- **Level**: 🟢 Low
- **Reason**: Additive change only, new method in existing class

### TODO

- [ ] Add `getAvailableBalance()` in `domain/point/Point.kt` - query current available balance (spec: point-spec.md#3.2)

### Tests

- [ ] Create `domain/point/PointTest.kt` - getAvailableBalance() returns correct remaining balance

### Done When

- [ ] `./gradlew test --tests "*PointTest*"` passes

---

- [ ] Milestone 2: Point partial usage

### Risk

- **Level**: 🟠 High
- **Reason**: Signature change to usePoint() affects PaymentFacade and RefundService

### TODO

- [ ] Modify[logic] `domain/point/Point.kt:use()` - change from full deduction to partial deduction (spec: point-spec.md#3.1)
- [ ] Modify[signature] `domain/point/PointService.kt:usePoint()` - add partial usage amount parameter (spec: point-spec.md#3.1)
- [ ] Modify `application/payment/PaymentFacade.kt:processPayment()` - pass amount to usePoint()
- [ ] Modify `application/refund/RefundService.kt:refundPoints()` - pass amount to usePoint()

### Check

- [ ] Check `domain/point/PointServiceIntegrationTest.kt` - calls usePoint(), signature changed
- [ ] Check `application/payment/PaymentFacadeIntegrationTest.kt` - uses PaymentFacade
- [ ] Check `application/refund/RefundServiceTest.kt` - uses RefundService

### Tests

- [ ] Update `domain/point/PointTest.kt` - use() with partial amount, exceeding balance case
- [ ] Update `domain/point/PointServiceTest.kt` - partial point usage with new parameter
- [ ] Update `domain/point/PointServiceIntegrationTest.kt` - integration test for partial usage
- [ ] Update `application/payment/PaymentFacadeIntegrationTest.kt` - payment with partial point usage

### Done When

- [ ] `./gradlew test --tests "*Point*"` passes
- [ ] `./gradlew test --tests "*PaymentFacade*"` passes
- [ ] `./gradlew test --tests "*RefundService*"` passes

---

- [ ] Milestone 3: PointUsageHistory entity definition

### Risk

- **Level**: 🟢 Low
- **Reason**: New entity, no existing code affected

### TODO

- [ ] Add `domain/point/PointUsageHistory.kt` - usage history entity, stores pointId only without entity relationship to Point (spec: point-spec.md#3.3, pattern: `domain/order/OrderHistory.kt`)

### Tests

- [ ] Create `domain/point/PointUsageHistoryTest.kt` - factory method, contains pointId/amount/usedAt fields

### Done When

- [ ] `./gradlew test --tests "*PointUsageHistoryTest*"` passes

---

- [ ] Milestone 4: PointUsageHistory persistence

### Risk

- **Level**: 🟢 Low
- **Reason**: New repository, follows existing pattern

### TODO

- [ ] Add `domain/point/PointUsageHistoryRepository.kt` - repository interface (pattern: `domain/order/OrderHistoryRepository.kt`)
- [ ] Add `infrastructure/point/JpaPointUsageHistoryRepository.kt` - JPA implementation (pattern: `infrastructure/order/JpaOrderHistoryRepository.kt`)

### Tests

- [ ] Create `infrastructure/point/JpaPointUsageHistoryRepositoryTest.kt` - save and findByPointId works correctly

### Done When

- [ ] `./gradlew test --tests "*PointUsageHistoryRepository*"` passes

---

- [ ] Milestone 5: Point usage history recording integration

### Risk

- **Level**: 🟡 Medium
- **Reason**: Modifies PointService, adds new dependency

### TODO

- [ ] Modify[field] `domain/point/PointService.kt` - add PointUsageHistoryRepository dependency
- [ ] Modify[logic] `domain/point/PointService.kt:usePoint()` - add usage history persistence after point usage

### Check

- [ ] Check `domain/point/PointServiceIntegrationTest.kt` - verify test configuration includes new repository
- [ ] Check DI configuration - ensure PointUsageHistoryRepository is registered

### Tests

- [ ] Update `domain/point/PointServiceIntegrationTest.kt`
    - point usage creates PointUsageHistory record
    - history contains correct pointId, amount, timestamp

### Done When

- [ ] `./gradlew test --tests "*PointServiceIntegrationTest*"` passes
- [ ] History record created on each point usage (verified by test)

---

## Spec Requirement Mapping

| Requirement | Spec Location | Milestone |
|-------------|---------------|-----------|
| Balance inquiry | point-spec.md#3.2 | Milestone 1 |
| Partial usage | point-spec.md#3.1 | Milestone 2 |
| Usage history entity | point-spec.md#3.3 | Milestone 3 |
| History persistence | point-spec.md#3.3 | Milestone 4 |
| History recording | point-spec.md#3.3 | Milestone 5 |

---

## Notes for Worker

- Milestone 3 and 4 can be executed in parallel with Milestone 1 and 2 if needed
- Milestone 5 requires both Milestone 2 (partial usage) and Milestone 4 (history persistence) complete
- PaymentFacade and RefundService changes in Milestone 2 are critical - verify all callers updated
