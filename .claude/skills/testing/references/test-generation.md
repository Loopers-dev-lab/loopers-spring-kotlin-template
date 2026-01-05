# Test Case Extraction

This document describes how to extract and generate test cases from spec documents for TDD workflow.

## Role

Test case extraction answers: "What test cases are needed to verify this milestone?"

The output is test skeletons - empty test methods with clear names that describe expected behavior. Implementer fills in actual test code later.

This enables true TDD: red tests exist before any implementation.

---

## Handling Spec-Code Gaps (TDD Essential)

When spec describes functionality NOT YET implemented:

```
Is this a TDD workflow (tests before implementation)?
├── Yes → Generate skeleton for spec requirement (RED phase)
│         The test SHOULD fail until implementation exists
└── No  → Document the gap, ask for clarification
```

**Key principle**: In TDD, tests are written BEFORE code exists. Generate skeletons for ALL spec requirements, even if implementation doesn't exist yet.

---

## Exception Type Mapping

Spec documents may use domain-specific names. Map to actual codebase patterns:

| Spec Describes | Actual Implementation Pattern |
|----------------|------------------------------|
| `CouponNotFoundException` | `CoreException(ErrorType.NOT_FOUND)` |
| `DuplicateCouponException` | `CoreException(ErrorType.CONFLICT)` |
| `InvalidAmountException` | `CoreException(ErrorType.BAD_REQUEST)` |

**Rule**: Check existing tests for exception patterns before generating skeletons.

---

## Test Case Extraction Process

### Step 1: Read Spec References

Read the spec sections specified in the milestone instruction and extract all testable requirements:

- Business rules that must be enforced
- Calculations that must be accurate
- State transitions that must be allowed or prevented
- Error conditions that must be handled

### Step 2: Determine Test Level

Determine which level each requirement belongs to (Unit, Integration, Concurrency, Adapter, E2E).

### Step 3: Extract Cases by Level

Follow the extraction patterns in the corresponding level-specific reference file.

### Step 4: Write Test Skeletons

Write each case as a test method following BDD structure:

- `@DisplayName` in Korean describing the behavior
- Given/When/Then comments as implementation hints
- `fail("Not implemented")` in every test body

---

## @Nested Class Organization

Structure @Nested classes by **outcome type**, not by scenario:

```kotlin
@DisplayName("CouponService")
class CouponServiceIntegrationTest {

    @Nested
    @DisplayName("issueCoupon")
    inner class IssueCoupon {
        // ✅ Success cases grouped
        @Test fun `assigns coupon when valid code`() { ... }
        @Test fun `assigns coupon when user has other coupons`() { ... }
    }

    @Nested
    @DisplayName("issueCoupon - 실패")
    inner class IssueCouponFailure {
        // ✅ Failure cases grouped
        @Test fun `throws NOT_FOUND when coupon not exists`() { ... }
        @Test fun `throws CONFLICT when already issued`() { ... }
    }
}
```

**Alternative**: If failure types are distinct and numerous, separate by error type:

```kotlin
@Nested @DisplayName("issueCoupon - NOT_FOUND") inner class IssueCouponNotFound { ... }
@Nested @DisplayName("issueCoupon - CONFLICT") inner class IssueCouponConflict { ... }
```

---

## Method Naming Convention

Pattern: `[verb phrase] when [condition]`

| Pattern | Example |
|---------|---------|
| Success | `assigns coupon when valid code` |
| Exception | `throws NOT_FOUND when coupon not exists` |
| State change | `decreases balance when deduct valid amount` |
| Boolean result | `returns true when user has permission` |

**Not allowed**:
- ❌ `testIssueCoupon` (no condition)
- ❌ `couponIssuedSuccessfully` (no "when")
- ❌ `should assign coupon` (avoid "should")

---

## Test File Scoping

When to create new test file vs extend existing:

| Situation | Action |
|-----------|--------|
| New method on existing class | Add @Nested to existing test file |
| Existing file > 500 lines | Consider splitting by method |
| New class | New test file |
| Focused test (concurrency, edge cases) | Separate test file with descriptive suffix |

**File naming examples**:
- `CouponServiceIntegrationTest.kt` - Main integration tests
- `CouponServiceConcurrencyTest.kt` - Concurrency-specific tests
- `CouponIssueLimitIntegrationTest.kt` - Feature-focused tests

---

## Given/When/Then Specificity

Specify **test-relevant** values only. Skip implementation details.

```kotlin
// ✅ GOOD: Concrete values that matter for the test
@Test
fun `throws CONFLICT when already issued`() {
    // Given: userId=1 이미 couponId=100을 발급받은 상태
    // When: 동일 userId로 동일 couponId 발급 요청
    // Then: CoreException(ErrorType.CONFLICT) 발생
    fail("Not implemented")
}

// ❌ BAD: Too much implementation detail
@Test
fun `throws CONFLICT when already issued`() {
    // Given: User entity (id=1, name="홍길동", email="test@test.com", createdAt=2025-01-01)
    //        exists in users table, IssuedCoupon entity with 12 fields exists...
    // ...
}

// ❌ BAD: Too vague
@Test
fun `throws CONFLICT when already issued`() {
    // Given: 사용자가 쿠폰을 가지고 있음
    // When: 발급 요청
    // Then: 에러
}
```

**Rule**: Include only values that would change the test outcome if different.

---

## One Behavior Per Test

Each test case must verify **one behavior**. If you're tempted to write multiple unrelated "Then" conditions, split into separate tests.

```kotlin
// ❌ Bad: Two unrelated behaviors in one test
@Test
@DisplayName("주문이 생성되고 사용자 주문 수가 증가한다")
fun `creates order and increments user order count`() {
    // Given: ...
    // When: Create order
    // Then: Order status is PLACED
    // Then: User's orderCount is incremented  ← different behavior
    fail("Not implemented")
}

// ✅ Good: Split into focused tests
@Test
@DisplayName("주문이 생성된다")
fun `creates order`() {
    // Given: ...
    // When: Create order
    // Then: Order status is PLACED
    fail("Not implemented")
}
```

## Handling Spec Changes

When milestone indicates changes to existing functionality:

1. Read the updated spec section
2. Read existing test file for the affected scope
3. Compare each existing test case against updated spec:
   - Spec unchanged for this case → Keep the case
   - Spec changed affecting this case → Rewrite with updated Given/When/Then
   - Case no longer valid per spec → Remove from output
4. Identify new cases required by updated spec → Add new skeletons

All cases in output use identical skeleton format with `fail("Not implemented")`.

The critical job is **deciding what to test** based on spec-to-test comparison.

## Quality Checklist (Generation)

- [ ] BDD structure is correct (@Nested per behavior, no more than 1 level of nesting)
- [ ] Naming convention is followed (Korean @DisplayName, English method name with `[result] when [condition]`)
- [ ] Every test body contains `fail("Not implemented")`
- [ ] Given/When/Then comments specify concrete values and expected results
- [ ] Verification does not exceed the responsibility of each level
- [ ] No duplicate verification across levels
- [ ] Each test verifies **one behavior** only
- [ ] "Then" describes observable outcomes (state/result/exception), NOT method calls

## Forbidden "Then" Patterns

| ❌ FORBIDDEN | ✅ ALLOWED |
|-------------|-----------|
| `repository.save() is called` | `Balance is 700` |
| `service.method() is invoked` | `Order status is PLACED` |
| `verify(mock).method()` | `Exception is thrown` |
| `no interaction with X` | `No order exists for userId` |
| `called N times` | `Response status is 201` |
