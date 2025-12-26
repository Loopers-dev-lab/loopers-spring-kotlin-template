---
name: testing
description: This skill should be used when "writing tests", "generating test skeletons", "deciding mock strategies", "learning test patterns", "understanding test conventions", "understanding test levels", "learning testing best practices", "테스트 작성", "테스트 패턴 파악", "테스트 패턴 이해", "테스트 컨벤션 이해", or "테스트 베스트 프랙티스 학습". Provides test case extraction, level-specific patterns (Unit, Integration, Concurrency, Adapter, E2E), BDD structure, factory method conventions, and quality guidelines following Classical TDD (state verification only, no mocks).
version: 1.1.0
---

# Testing Skill

Test writing standards, test case extraction rules, and quality guidelines for Classical TDD workflow.

## Core Philosophy

Tests serve three purposes: verify correctness, document behavior, and enable safe refactoring.

- **Tests as Executable Documentation**: Tests are the most accurate documentation because they're always up-to-date.
- **Tests Enable Fearless Refactoring**: With good tests, you can safely change implementation. But this only works if
  tests verify behavior, not implementation.
- **Tests Reveal Design Problems**: If a test is hard to write, that's feedback about your design.

## CRITICAL: State/Result Verification ONLY (Classical TDD)

This project follows **Classical TDD (Detroit School)**. All tests MUST verify **outcomes**, NOT **interactions**.

> **Verify WHAT happened, not HOW it happened.**

### ✅ ALLOWED

```kotlin
assertThat(result).isEqualTo(expected)
assertThat(point.balance).isEqualTo(700L)
assertThatThrownBy { point.use(500L) }.isInstanceOf(CoreException::class.java)
```

### ❌ FORBIDDEN

```kotlin
verify(repository).save(any())
verify(mock, times(1)).method()
verifyNoInteractions(mock)
```

## Test Level Overview

For level classification criteria, decision flow, and file naming conventions, see @references/test-level-guide.md

## BDD Structure

### Nested Classes

Use `@Nested` per behavior (method/endpoint). No more than 1 level of nesting.

```kotlin
@Nested
@DisplayName("use")
inner class Use {
    // All cases for use()
}
```

### Naming Convention

- `@DisplayName`: Korean description
- Method name: English with backticks, `[result] when [condition]`

### Given/When/Then

Every test must have comments specifying concrete values and expected results.

```kotlin
@Test
@DisplayName("주문 금액이 올바르게 계산된다")
fun `calculates total correctly`() {
    // given
    val initialBalance = 1000L
    val point = createPoint(balance = initialBalance)

    // when
    val deductAmount = 300L
    point.deduct(deductAmount)

    // then
    assertThat(point.balance).isEqualTo(initialBalance - deductAmount)
}
```

## Factory Method Pattern

Every test class must have private factory methods with **all parameters defaulted**.

```kotlin
// Unit Test: domain object creation
private fun createPoint(
    id: Long = 0L,
    userId: Long = 1L,
    balance: Long = 1000L,
    status: PointStatus = PointStatus.ACTIVE,
): Point = Point.of(id, userId, balance, status)

// Integration Test: includes DB persistence
private fun createProduct(
    price: Money = Money.krw(10000),
    stockQuantity: Int = 100,
): Product {
    val brand = brandRepository.save(Brand.create("Test Brand"))
    val product = productRepository.save(Product.create(name = "Test Product", price = price, brand = brand))
    stockRepository.save(Stock.create(product.id, stockQuantity))
    return product
}
```

## Essential Rules

### Expose Only What Matters

```kotlin
// ❌ Bad: What is this test about?
val point = Point.of(id = 1L, userId = 42L, balance = 1000L, status = PointStatus.ACTIVE)

// ✅ Good: Clearly about balance deduction
val point = createPoint(balance = 1000L)
```

### Single Logical Assertion

Each test verifies one behavior. Multiple `assertThat` is fine if they verify aspects of the same result.

### Meaningful Variable Names

```kotlin
// ❌ Bad
assertThat(result).isEqualTo(700)

// ✅ Good
val initialBalance = 1000L
val deductAmount = 300L
assertThat(point.balance).isEqualTo(initialBalance - deductAmount)
```

### Test Isolation

- No shared mutable state
- Database cleanup in `@AfterEach`
- No test interdependence

## When to Skip Test Generation

Pure data objects with no behavior:

- **Command** - use case input (e.g., `CreateOrderCommand`)
- **Event** - immutable fact record (e.g., `OrderCreatedEvent`)
- **DTO / Request / Response** - data transfer only

---

## References

Load references based on the current task. Each file provides detailed patterns and real code examples.

### When determining test level

- @references/test-level-guide.md - Level classification criteria and decision flow

### When generating test skeletons

- @references/test-generation.md - Spec to test skeleton process, quality checklist

### When writing tests by level

- @references/unit-test.md - Unit test patterns (state change, validation, ParameterizedTest, domain events)
- @references/integration-test.md - Integration patterns (rollback, Spring Event, Kafka Consumer)
- @references/concurrency-test.md - Concurrency patterns (thread pool, locking, idempotency)
- @references/adapter-test.md - Adapter patterns (WireMock, Circuit Breaker, Retry, complex queries)
- @references/e2e-test.md - E2E patterns (HTTP status codes, auth failures, API contract)

### When deciding external dependencies strategy

- @references/external-dependencies.md - External dependencies by test level (Real DB, WireMock, Testcontainers)
