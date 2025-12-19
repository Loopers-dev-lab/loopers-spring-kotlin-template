# Test Case Extraction

This document describes how to extract and generate test cases from spec documents for TDD workflow.

## Role

Test case extraction answers: "What test cases are needed to verify this milestone?"

The output is test skeletons - empty test methods with clear names that describe expected behavior. Implementer fills in actual test code later.

This enables true TDD: red tests exist before any implementation.

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
