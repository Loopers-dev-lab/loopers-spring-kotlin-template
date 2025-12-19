# Implementation Plan: Event Publishing Migration

**Created**: 2025-12-19
**Specs**: `docs/specs/round8-spec-4-event-publishing-migration.md`
**Research**: `research.md`

---

## Overview

This plan implements the event publishing migration for ProductStatistic metrics (sales quantity, view count) and cache invalidation when stock is depleted. It adds 3 new domain events and modifies 9 existing components.

## Infrastructure Decisions

1. **Event Publishing Pattern**: Follow existing Spring `ApplicationEventPublisher` pattern with `@TransactionalEventListener(BEFORE_COMMIT)` for Outbox synchronization
2. **Domain Event Storage**: Use `@Transient` field with lazy initialization (existing Order pattern)
3. **Event Factory Pattern**: Use `from()` for entity-based events, `create()` for non-entity events
4. **Idempotency**: Existing idempotency checks prevent duplicate event publishing (early return before event registration)

---

## Milestones Overview

| # | Title | Scope | Dependencies |
|---|-------|-------|--------------|
| 1 | OrderPaidEventV1 event class | Create event data class | None |
| 2 | ProductViewedEventV1 event class | Create event data class | None |
| 3 | StockDepletedEventV1 event class | Create event data class | None |
| 4 | Order.pay() event registration | Modify domain entity | Milestone 1 |
| 5 | Stock domain event infrastructure | Add event support to Stock entity | Milestone 3 |
| 6 | OrderService event publishing | Modify completePayment() | Milestone 4 |
| 7 | ProductService event publishing | Add eventPublisher, modify decreaseStocks() | Milestone 5 |
| 8 | ProductFacade event publishing | Add eventPublisher, modify findProductById() | Milestone 2 |
| 9 | ProductV1ApiSpec userId parameter | Add X-USER-ID header to interface | Milestone 8 |
| 10 | ProductV1Controller userId parameter | Add X-USER-ID header to controller | Milestone 9 |
| 11 | EventTypeResolver new mappings | Add 3 event type mappings | Milestones 1, 2, 3 |
| 12 | OutboxEventListener new mappings | Add 3 aggregate extraction mappings | Milestones 1, 2, 3 |

---

## Milestone Details

- [x] Milestone 1: OrderPaidEventV1 event class

### TODO

- [x] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderPaidEventV1.kt` - order payment completed event with orderItems snapshot (spec: spec#5.1, pattern: `domain/order/OrderCanceledEventV1.kt:L5-29`)

### Tests

- [x] Create `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderPaidEventV1Test.kt` - from() factory creates event with correct properties

### Done When

- [x] `./gradlew :apps:commerce-api:compileKotlin` passes
- [x] `./gradlew :apps:commerce-api:test --tests "*OrderPaidEventV1Test"` passes

---

- [x] Milestone 2: ProductViewedEventV1 event class

### TODO

- [x] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductViewedEventV1.kt` - product view event with optional userId (spec: spec#5.2, pattern: `domain/like/LikeCreatedEventV1.kt:L5-9`)

### Tests

- [x] Create `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductViewedEventV1Test.kt` - create() factory with and without userId

### Done When

- [x] `./gradlew :apps:commerce-api:compileKotlin` passes
- [x] `./gradlew :apps:commerce-api:test --tests "*ProductViewedEventV1Test"` passes

---

- [ ] Milestone 3: StockDepletedEventV1 event class

### TODO

- [ ] Create `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/StockDepletedEventV1.kt` - stock depleted event with productId and stockId (spec: spec#5.3, pattern: `domain/payment/PaymentPaidEventV1.kt:L5-18`)

### Tests

- [ ] Create `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/StockDepletedEventV1Test.kt` - from() factory creates event with correct properties

### Done When

- [ ] `./gradlew :apps:commerce-api:compileKotlin` passes
- [ ] `./gradlew :apps:commerce-api:test --tests "*StockDepletedEventV1Test"` passes

---

- [ ] Milestone 4: Order.pay() event registration

### TODO

- [ ] Modify[logic] `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/Order.kt:pay()` - add `getDomainEvents().add(OrderPaidEventV1.from(this))` after status change (spec: spec#5.4, pattern: `Order.kt:cancel():L125-134`)
- [ ] Add import for `OrderPaidEventV1` in `Order.kt`

### Check

- [ ] Check `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderTest.kt` - may need update for pay() event verification

### Tests

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderTest.kt` - add test in Pay nested class: "pay() success registers OrderPaidEventV1"
- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderTest.kt` - add test in Pay nested class: "pay() when already PAID does not register event" (idempotency)

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*OrderTest"` passes
- [ ] Event is registered only on successful state transition (not on idempotent no-op)

---

- [ ] Milestone 5: Stock domain event infrastructure

### TODO

- [ ] Modify[field] `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/Stock.kt` - add `@Transient private var domainEvents: MutableList<DomainEvent>? = null` (spec: spec#5.5, pattern: `domain/order/Order.kt:L54-68`)
- [ ] Add `getDomainEvents()` private method in `Stock.kt` - lazy initialization (pattern: `domain/order/Order.kt:L57-62`)
- [ ] Add `pollEvents()` public method in `Stock.kt` - return copy and clear (pattern: `domain/order/Order.kt:L64-68`)
- [ ] Modify[logic] `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/Stock.kt:decrease()` - add event registration when quantity == 0 (spec: spec#5.5)
- [ ] Add imports for `DomainEvent`, `StockDepletedEventV1`, `Transient` in `Stock.kt`

### Check

- [ ] Check `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/StockTest.kt` - may need update for decrease() event verification

### Tests

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/StockTest.kt` - add test: "decrease() registers StockDepletedEventV1 when quantity becomes 0"
- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/StockTest.kt` - add test: "decrease() does not register event when quantity > 0"
- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/StockTest.kt` - add test: "pollEvents() returns events and clears internal list"

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*StockTest"` passes
- [ ] Event is registered only when quantity reaches exactly 0

---

- [ ] Milestone 6: OrderService event publishing

### TODO

- [ ] Modify[logic] `apps/commerce-api/src/main/kotlin/com/loopers/domain/order/OrderService.kt:completePayment()` - add event publishing after save (spec: spec#5.12, pattern: `OrderService.kt:cancelOrder():L48-64`)

### Check

- [ ] Check `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderServiceIntegrationTest.kt` - may need update for completePayment() event verification

### Tests

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/domain/order/OrderServiceIntegrationTest.kt` - add test: "completePayment() publishes OrderPaidEventV1"

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*OrderServiceIntegrationTest"` passes

---

- [ ] Milestone 7: ProductService event publishing

### TODO

- [ ] Modify[signature] `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt` constructor - add `private val eventPublisher: ApplicationEventPublisher` (spec: spec#5.10, pattern: `domain/order/OrderService.kt:L10-14`)
- [ ] Modify[logic] `apps/commerce-api/src/main/kotlin/com/loopers/domain/product/ProductService.kt:decreaseStocks()` - add `lockedStocks.flatMap { it.pollEvents() }.forEach { eventPublisher.publishEvent(it) }` after saveAll (spec: spec#5.11)
- [ ] Add import for `ApplicationEventPublisher` in `ProductService.kt`

### Check

- [ ] Check `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt` - may need mock for eventPublisher

### Tests

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/domain/product/ProductServiceIntegrationTest.kt` - add test: "decreaseStocks() publishes StockDepletedEventV1 when stock reaches 0"

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*ProductServiceIntegrationTest"` passes

---

- [ ] Milestone 8: ProductFacade event publishing

### TODO

- [ ] Modify[signature] `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductFacade.kt` constructor - add `private val eventPublisher: ApplicationEventPublisher` (spec: spec#5.7, pattern: `domain/order/OrderService.kt:L10-14`)
- [ ] Modify[signature] `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductFacade.kt:findProductById()` - add `userId: Long? = null` parameter (spec: spec#5.7)
- [ ] Modify[logic] `apps/commerce-api/src/main/kotlin/com/loopers/application/product/ProductFacade.kt:findProductById()` - add `eventPublisher.publishEvent(ProductViewedEventV1.create(id, userId))` before return (spec: spec#5.7)
- [ ] Add imports for `ApplicationEventPublisher`, `ProductViewedEventV1` in `ProductFacade.kt`

### Check

- [ ] Check `apps/commerce-api/src/test/kotlin/com/loopers/application/product/ProductFacadeIntegrationTest.kt` - may need update for findProductById() signature change

### Tests

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/application/product/ProductFacadeIntegrationTest.kt` - add test in FindProductById nested class: "findProductById() publishes ProductViewedEventV1"
- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/application/product/ProductFacadeIntegrationTest.kt` - add test in FindProductById nested class: "findProductById() with userId publishes event with userId"

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*ProductFacadeIntegrationTest"` passes

---

- [ ] Milestone 9: ProductV1ApiSpec userId parameter

### TODO

- [ ] Modify[signature] `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1ApiSpec.kt:getProduct()` - add userId parameter with @Parameter annotation (spec: spec#5.9, pattern: see research.md#3.13)
- [ ] Add import for `ParameterIn` in `ProductV1ApiSpec.kt` if not present

### Done When

- [ ] `./gradlew :apps:commerce-api:compileKotlin` passes

---

- [ ] Milestone 10: ProductV1Controller userId parameter

### TODO

- [ ] Modify[signature] `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1Controller.kt:getProduct()` - add `@RequestHeader(value = "X-USER-ID", required = false) userId: Long?` parameter (spec: spec#5.8, pattern: see research.md#3.12)
- [ ] Modify[logic] `apps/commerce-api/src/main/kotlin/com/loopers/interfaces/api/product/ProductV1Controller.kt:getProduct()` - pass userId to `productFacade.findProductById(productId, userId)`
- [ ] Add import for `RequestHeader` in `ProductV1Controller.kt` if not present

### Check

- [ ] Check `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/product/ProductV1ApiE2ETest.kt` - may need update for getProduct() with X-USER-ID header

### Tests

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/interfaces/api/product/ProductV1ApiE2ETest.kt` - verify getProduct() accepts X-USER-ID header

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*ProductV1ApiE2ETest"` passes

---

- [ ] Milestone 11: EventTypeResolver new mappings

### TODO

- [ ] Modify[logic] `apps/commerce-api/src/main/kotlin/com/loopers/support/outbox/EventTypeResolver.kt:resolve()` - add 3 new mappings before else clause (spec: spec#5.13, pattern: existing mappings L13-19)
  - `is OrderPaidEventV1 -> "loopers.order.paid.v1"`
  - `is ProductViewedEventV1 -> "loopers.product.viewed.v1"`
  - `is StockDepletedEventV1 -> "loopers.stock.depleted.v1"`
- [ ] Add imports for `OrderPaidEventV1`, `ProductViewedEventV1`, `StockDepletedEventV1` in `EventTypeResolver.kt`

### Check

- [ ] Check `apps/commerce-api/src/test/kotlin/com/loopers/support/outbox/EventTypeResolverTest.kt` - existing test file, add new event test cases

### Tests

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/support/outbox/EventTypeResolverTest.kt` - add nested class for new events with tests for resolve() returning correct type strings

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*EventTypeResolverTest"` passes

---

- [ ] Milestone 12: OutboxEventListener new mappings

### TODO

- [ ] Modify[logic] `apps/commerce-api/src/main/kotlin/com/loopers/infrastructure/outbox/OutboxEventListener.kt:extractAggregate()` - add 3 new mappings before else clause (spec: spec#5.14, pattern: existing mappings L37-43)
  - `is OrderPaidEventV1 -> "Order" to event.orderId.toString()`
  - `is ProductViewedEventV1 -> "Product" to event.productId.toString()`
  - `is StockDepletedEventV1 -> "Stock" to event.productId.toString()`
- [ ] Add imports for `OrderPaidEventV1`, `ProductViewedEventV1`, `StockDepletedEventV1` in `OutboxEventListener.kt`

### Check

- [ ] Check `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/outbox/OutboxEventListenerIntegrationTest.kt` - existing test file for OutboxEventListener

### Tests

- [ ] Update `apps/commerce-api/src/test/kotlin/com/loopers/infrastructure/outbox/OutboxEventListenerIntegrationTest.kt` - add tests verifying new events create correct Outbox records

### Done When

- [ ] `./gradlew :apps:commerce-api:test --tests "*OutboxEventListenerIntegrationTest"` passes

---

## Spec Requirement Mapping

| Requirement | Spec Location | Milestone |
|-------------|---------------|-----------|
| OrderPaidEventV1 data class | spec#5.1 | Milestone 1 |
| ProductViewedEventV1 data class | spec#5.2 | Milestone 2 |
| StockDepletedEventV1 data class | spec#5.3 | Milestone 3 |
| Order.pay() event registration | spec#5.4 | Milestone 4 |
| Stock pollEvents() and decrease() event | spec#5.5 | Milestone 5 |
| ProductService eventPublisher | spec#5.10 | Milestone 7 |
| ProductService.decreaseStocks() event publishing | spec#5.11 | Milestone 7 |
| OrderService.completePayment() event publishing | spec#5.12 | Milestone 6 |
| ProductFacade eventPublisher and findProductById() | spec#5.7 | Milestone 8 |
| ProductV1Controller X-USER-ID header | spec#5.8 | Milestone 10 |
| ProductV1ApiSpec X-USER-ID parameter | spec#5.9 | Milestone 9 |
| EventTypeResolver 3 new mappings | spec#5.13 | Milestone 11 |
| OutboxEventListener 3 new mappings | spec#5.14 | Milestone 12 |

---

## Notes for Worker

1. **Event Class Structure**:
   - `OrderPaidEventV1`: Include nested `OrderItemSnapshot` with productId, quantity
   - `ProductViewedEventV1`: Use `create()` factory (not `from()`) since not created from entity
   - `StockDepletedEventV1`: Use `from(stock: Stock)` factory

2. **Import Dependencies**:
   - When adding event mappings to EventTypeResolver and OutboxEventListener, ensure imports are added
   - EventTypeResolver and OutboxEventListener must be updated together to avoid runtime errors

3. **Idempotency Consideration**:
   - Order.pay() has idempotency check (early return if PAID) - event should NOT be registered on idempotent no-op
   - Ensure event registration happens AFTER the idempotency check passes

4. **Test Patterns**:
   - Follow existing test patterns in OrderTest.kt for event verification via pollEvents()
   - Use @Nested classes for grouping related tests
   - Use Korean @DisplayName and English method names in backticks

5. **Topic Resolution** (automatic via TopicResolver):
   - `loopers.order.paid.v1` -> `order-events`
   - `loopers.product.viewed.v1` -> `product-events`
   - `loopers.stock.depleted.v1` -> `stock-events`

6. **Milestone Execution Order**:
   - Milestones 1-3 can be executed in parallel (no dependencies)
   - Milestones 4-5 depend on event classes (1, 3)
   - Milestones 6-8 depend on domain modifications (4, 5, 2)
   - Milestones 9-10 are sequential (interface before implementation)
   - Milestones 11-12 depend only on event classes (1, 2, 3)
