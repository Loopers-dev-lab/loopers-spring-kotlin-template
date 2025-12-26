# Milestone Guide

Complete guide for writing milestones. Read this when creating milestone details.

---

## 1. Milestone Structure

Every milestone follows this structure:

```markdown
- [ ] Milestone N: [Responsibility Title]

### Risk
- **Level**: 🟢 Low | 🟡 Medium | 🟠 High | 🔴 Critical
- **Reason**: [Why this risk level]

### Clarifications (if any)
- [ ] [Question that must be answered before implementation]

### TODO
- [ ] [Action] `[path]` - [description] (spec: [ref], pattern: `[file:lines]`)

### Check
- [ ] Check `[path]` - [why this file might be affected]

### Tests
- [ ] [Create/Update] `[test_path]` - [what to verify]

### Done When
- [ ] `./gradlew test --tests "*TestName*"` passes
- [ ] [Other verifiable criterion]
```

---

## 2. Section Details

### Risk

Assess overall milestone risk level with reason.

```markdown
### Risk
- **Level**: 🟠 High
- **Reason**: Signature change affects 3 callers, schema migration included
```

Risk determines how carefully this milestone should be executed and reviewed.

### Clarifications

Business ambiguities that block implementation. Only for genuine ambiguities where spec is unclear.

```markdown
### Clarifications
- [ ] Confirm: Can partially used points be cancelled? (spec 2.4 unclear)
- [ ] Confirm: Should expired points trigger notification? (not in spec)
```

Do NOT include technical decisions here. Technical decisions belong to implementer.

### TODO

What to create, modify, or delete. Each item needs:

1. **Action type**: Add, Modify[signature], Modify[logic], Modify[field], Delete
2. **File path**: Exact path from project root
3. **Description**: What to do
4. **Spec reference**: Which requirement this fulfills
5. **Pattern reference**: Existing code to follow (optional but recommended)

```markdown
### TODO
- [ ] Add `domain/point/PointUsageHistory.kt` - usage history entity (spec: point-spec.md#3.3, pattern: `domain/order/OrderHistory.kt`)
- [ ] Modify[signature] `domain/point/PointService.kt:usePoint()` - add amount parameter (spec: point-spec.md#3.1)
- [ ] Modify[logic] `domain/point/Point.kt:use()` - change to partial deduction (spec: point-spec.md#3.1)
```

**Item-level risk** (optional): Add when specific item is riskier than overall milestone.

```markdown
- [ ] Delete `domain/point/OldPointCalculator.kt` **Risk: 🔴 Critical - verify no remaining usages**
```

### Check

Files that MAY be affected by this milestone's changes. Used for:

1. Impact analysis before implementation
2. Validation that nothing was missed

```markdown
### Check
- [ ] Check `application/payment/PaymentFacade.kt` - calls usePoint(), signature changed
- [ ] Check `domain/point/PointServiceTest.kt` - tests usePoint()
- [ ] Check `infrastructure/point/PointRepositoryImpl.kt` - might need update for new field
```

**CRITICAL: TODO vs Check**

This is different from TODO:
- **TODO items MUST be changed** - they are the work of this milestone
- **Check items MIGHT need changes** - implementer must verify and update if needed

**When to add Check items:**

- Tests that import modified production code
- Callers of modified methods (if logic change might break assumptions)
- Files that instantiate modified entities
- Configuration files that reference modified classes

**CRITICAL**: For `Modify[signature]`, ALL callers must be either in TODO (if definitely needs change) or Check (if might need change).

### Tests

What tests to create or update.

```markdown
### Tests
- [ ] Create `domain/point/PointUsageHistoryTest.kt` - factory method, contains pointId/amount/usedAt
- [ ] Update `domain/point/PointServiceTest.kt` - partial usage with new parameter
- [ ] Update `application/payment/PaymentFacadeIntegrationTest.kt` - reflects new behavior
```

### Done When

Verifiable completion criteria. Must be executable commands or measurable checks.

```markdown
### Done When
- [ ] `./gradlew test --tests "*PointUsageHistory*"` passes
- [ ] `./gradlew test --tests "*PointService*"` passes
- [ ] All 4 state transitions from spec 2.3 have corresponding tests
```

---

## 3. Action Types

Use correct action types. These enable accurate impact analysis.

| Action | When to Use | Implication |
|--------|-------------|-------------|
| `Create` | New file | Low risk, completely additive |
| `Add` | New method/field in existing file (no existing code affected) | Low risk, additive |
| `Modify[signature]` | Change method parameters, return type, or constructor | **All callers must update** |
| `Modify[logic]` | Change implementation without signature change | Tests may need update |
| `Modify[field]` | Add/remove/change class fields | Check serialization, DB mapping, instantiation sites |
| `Delete` | Remove file or method | **Verify no usages first** |

### Create vs Add

**Create**: New file that doesn't exist yet.
```markdown
- [ ] Create `domain/point/PointUsageHistory.kt` - new entity
```

**Add**: New method or field in an existing file. No existing code is affected.
```markdown
- [ ] Add `getAvailableBalance()` in `domain/point/Point.kt` - new query method
```

The distinction matters: `Create` has no impact on existing code, while `Add` requires verifying the existing file's structure.

### Why Action Type Matters

**CRITICAL**: Validator uses action type to determine what to analyze:

| Action | Validator Analysis |
|--------|-------------------|
| `Modify[signature]` | Find all callers, verify they're in same milestone or Check section |
| `Modify[logic]` | Find tests that assert on behavior |
| `Modify[field]` | Find all instantiation sites |
| `Delete` | Verify no remaining usages |

Incorrect action types lead to missed dependency detection and broken milestones.

### Action Type Examples

**Create**
```markdown
- [ ] Create `domain/point/PointUsageHistory.kt` - new entity
- [ ] Create `infrastructure/point/JpaPointUsageHistoryRepository.kt` - new repository impl
```

**Add**
```markdown
- [ ] Add `getAvailableBalance()` in `domain/point/Point.kt` - new query method
- [ ] Add `validateStateTransition()` in `domain/point/Point.kt` - new validation method
```

**Modify[signature]**
```markdown
- [ ] Modify[signature] `PointService.kt:usePoint()` - add `amount: Long` parameter
- [ ] Modify[signature] `Point.kt` constructor - add `expiresAt: LocalDateTime`
```

**Modify[logic]**
```markdown
- [ ] Modify[logic] `Point.kt:use()` - change from full to partial deduction
- [ ] Modify[logic] `PointService.kt:calculateBalance()` - exclude expired points
```

**Modify[field]**
```markdown
- [ ] Modify[field] `Point.kt` - add `usedAmount: Long` field
```

**Delete**
```markdown
- [ ] Delete `domain/point/OldPointCalculator.kt` - deprecated, replaced by Point.calculate()
```

---

## 4. Boundary Rules

### CRITICAL: Green State

Each milestone must leave codebase:
- Compilable
- All tests passing

### Change Propagation

When you modify code, identify everything affected.

**Type 1: Signature Changes → Compilation Failure**

| Change | What Breaks |
|--------|-------------|
| Add required parameter | All callers won't compile |
| Change return type | All receivers won't compile |
| Rename public method | All callers reference old name |
| Add required constructor field | All instantiation sites break |
| Change interface method | All implementations break |

**Type 2: Logic Changes → Test Failure**

| Change | What Breaks |
|--------|-------------|
| Change calculation logic | Tests asserting old result |
| Change validation rules | Tests expecting old validation |
| Change state transitions | Tests verifying old flow |
| Change error messages | Tests asserting old messages |
| Change default values | Tests relying on old defaults |

**Type 3: Structural Changes → Runtime Failure**

| Change | Symptom |
|--------|---------|
| Interface impl replacement | `NoUniqueBeanDefinitionException` |
| Schema + entity mismatch | `SchemaManagementException` |
| Bean qualifier removal | `NoSuchBeanDefinitionException` |
| Config property rename | `BeanCreationException` |

### Atomic Operations

Some changes must NEVER be split:

**Interface Replacement**
```markdown
# WRONG
- [ ] Milestone 1: Add NewPaymentServiceImpl
- [ ] Milestone 2: Delete OldPaymentServiceImpl  # Broken between milestones!

# CORRECT
- [ ] Milestone 1: Replace PaymentService implementation
    - Add NewPaymentServiceImpl
    - Delete OldPaymentServiceImpl
```

**Schema + Entity**
```markdown
# WRONG
- [ ] Milestone 1: Add column to DB migration
- [ ] Milestone 2: Add field to Entity  # Broken between milestones!

# CORRECT
- [ ] Milestone 1: Add expiresAt to Point
    - Add migration for expires_at column
    - Add expiresAt field to Point entity
```

**Signature + All Callers**
```markdown
# WRONG
- [ ] Milestone 1: Add amount parameter to usePoint()
- [ ] Milestone 2: Update PaymentFacade to pass amount  # Won't compile!

# CORRECT
- [ ] Milestone 1: Add partial usage support
    - Modify[signature] usePoint() - add amount parameter
    - Update PaymentFacade.processPayment() - pass amount
    - Update RefundService.refundPoints() - pass amount
    - Update all tests
```

### Safe Pattern: Gradual Migration

When atomic change is too risky, use backward-compatible intermediate steps.

```markdown
- [ ] Milestone 1: Add new method (additive)
### TODO
- [ ] Add `usePointPartial(amount: Long)` in PointService.kt

- [ ] Milestone 2: Migrate callers
### TODO
- [ ] Update PaymentFacade to use usePointPartial()
- [ ] Update RefundService to use usePointPartial()

- [ ] Milestone 3: Remove old method
### TODO
- [ ] Delete `usePoint()` from PointService.kt  # Now safe
```

Each milestone is green. More milestones, but each is safe.

---

## 5. Risk Classification

| Level | When to Use | Example |
|-------|-------------|---------|
| 🟢 Low | New files, additive code, documentation | Create new entity |
| 🟡 Medium | Modify existing logic, config changes | Add method to service |
| 🟠 High | Signature changes, schema changes, dependency updates | Change method parameters |
| 🔴 Critical | Deletions, interface replacement, breaking changes | Remove deprecated class |

### Risk Assessment Checklist

**🟢 Low if all true:**
- [ ] Only adding new files/methods
- [ ] No existing code modified
- [ ] No tests should break

**🟡 Medium if any true:**
- [ ] Modifying existing logic
- [ ] Tests need updates
- [ ] Configuration changes

**🟠 High if any true:**
- [ ] Signature changes (parameters, return types)
- [ ] Schema/migration changes
- [ ] Multiple files affected
- [ ] Cross-layer changes (domain + application + infrastructure)

**🔴 Critical if any true:**
- [ ] Deleting files or methods
- [ ] Replacing interface implementations
- [ ] Breaking API changes
- [ ] Security-related changes

---

## 6. Good vs Bad Examples

### TODO Section

**Bad: Missing references**
```markdown
- [ ] Add point usage method
```

**Good: Complete references**
```markdown
- [ ] Add `use(amount: Long)` in `domain/point/Point.kt` - deduct points with balance validation (spec: point-spec.md#2.3.1, pattern: `domain/coupon/Coupon.kt:L45-60`)
```

### Check Section

**Bad: Missing callers for signature change**
```markdown
### TODO
- [ ] Modify[signature] `PointService.kt:usePoint()` - add amount parameter

### Check
- [ ] Check `PointServiceTest.kt`
# Missing: PaymentFacade, RefundService that also call usePoint()!
```

**Good: All callers included**
```markdown
### TODO
- [ ] Modify[signature] `PointService.kt:usePoint()` - add amount parameter

### Check
- [ ] Check `PaymentFacade.kt:processPayment()` - calls usePoint()
- [ ] Check `RefundService.kt:refundPoints()` - calls usePoint()
- [ ] Check `PointServiceTest.kt` - tests usePoint()
- [ ] Check `PaymentFacadeIntegrationTest.kt` - uses PaymentFacade
```

### Done When Section

**Bad: Vague criteria**
```markdown
### Done When
- [ ] Point usage works
- [ ] Tests pass
```

**Good: Executable criteria**
```markdown
### Done When
- [ ] `./gradlew test --tests "*PointTest"` passes
- [ ] `./gradlew test --tests "*PointServiceIntegrationTest"` passes
- [ ] All 3 usage scenarios from spec 2.3 have corresponding tests
```

### Milestone Boundary

**Bad: Split atomic operation**
```markdown
- [ ] Milestone 3: Add new PointService implementation
### TODO
- [ ] Add `NewPointServiceImpl.kt` implementing `PointService`

- [ ] Milestone 4: Remove old implementation
### TODO
- [ ] Delete `OldPointServiceImpl.kt`
# BROKEN: NoUniqueBeanDefinitionException between milestone 3 and 4!
```

**Good: Atomic operation together**
```markdown
- [ ] Milestone 3: Replace PointService implementation
### TODO
- [ ] Add `NewPointServiceImpl.kt` implementing `PointService`
- [ ] Delete `OldPointServiceImpl.kt`
- [ ] Update tests to verify new behavior
```

---

## 7. Pre-Finalization Checklist

Before finalizing any milestone:

**Structure**
- [ ] Risk level assigned with reason
- [ ] Every TODO has: action type + file path + description + spec reference
- [ ] Pattern references for non-trivial implementations
- [ ] Done When has executable test commands

**Green State**
- [ ] No atomic operations split
- [ ] Signature changes include ALL callers in Check
- [ ] Logic changes include affected tests in Tests section

**Self-Containment**
- [ ] Milestone understandable without reading other milestones
- [ ] All file paths are complete (from project root)
- [ ] Dependencies on previous milestones noted if any
