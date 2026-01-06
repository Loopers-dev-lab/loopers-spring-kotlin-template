# Planning Workflow

Step-by-step process for creating a plan. Read this when following the planning process.

---

## Overview

```
Phase 1: Absorb Inputs
    ↓
Phase 2: Identify Clarifications  
    ↓
Phase 3: Design Milestones
    ↓
Phase 4: Review and Finalize
    ↓
Phase 5: Validate Plan
```

---

## Phase 1: Absorb Inputs

### Step 1.1: Find and Read Research

Look for `research.md` in the project. If it exists, read completely and extract:

- **Integration points**: Files and methods to modify
- **Patterns**: How similar features are implemented
- **Reference code**: Exact locations to follow
- **Considerations**: Hidden complexities, dependencies

If research.md doesn't exist, you may need to do codebase analysis yourself or request it.

### Step 1.2: Read Spec Documents

Find spec documents (typically in `docs/specs/` or similar). Read all relevant specs and extract:

- **Requirements**: What must be built
- **Business rules**: Logic to implement
- **State transitions**: Allowed state changes
- **Error cases**: What must fail and how

### Step 1.3: Identify Gaps

Compare specs with codebase understanding:

- What technical decisions aren't in spec?
- What might be ambiguous?
- What needs clarification before implementation?

### Phase 1 Checklist

- [ ] Research findings reviewed (integration points, patterns, references)
- [ ] All relevant specs read
- [ ] Requirements extracted
- [ ] Business rules understood
- [ ] Gaps and ambiguities noted

---

## Phase 2: Identify Clarifications

### Step 2.1: Categorize Ambiguities

For each gap identified:

**Business Ambiguity** → Add to Clarifications section in relevant milestone
- Spec is genuinely unclear
- Multiple valid interpretations exist
- Business decision needed

**Technical Decision** → Leave to implementer
- Library choice
- Algorithm selection
- Locking strategy
- These are NOT clarifications

### Decision Boundary Reference

| Decision Type | Who Decides | Example |
|--------------|-------------|---------|
| Library choice | Implementer | "Use Kotlin coroutines vs RxJava" |
| Algorithm | Implementer | "Binary search vs linear search" |
| Error message text | Implementer | "Invalid input" wording |
| Validation **rules** | Spec author (Clarification) | "What counts as valid?" |
| State transitions | Spec author (Clarification) | "Can X go to Y state?" |
| Business logic | Spec author (Clarification) | "What happens when...?" |

**Red Flag**: If you're deciding WHAT should happen (not HOW), it's a business decision. Surface it in Clarifications.

### Step 2.2: Note Blocking Dependencies

For each clarification:
1. Note the question clearly
2. Note which milestone it blocks
3. Note what happens if each answer is chosen (if applicable)

```markdown
### Clarifications
- [ ] Confirm: Can partially used points be cancelled? (spec 2.4 unclear)
      - If yes: Need cancellation flow in this milestone
      - If no: Simpler implementation, skip cancellation
```

### Phase 2 Checklist

- [ ] Business ambiguities identified
- [ ] Each ambiguity linked to a milestone
- [ ] Technical decisions left to implementer (not in clarifications)

---

## Phase 3: Design Milestones

### Step 3.1: List All Deliverables

From specs and research, list everything to create:

- New files (entities, services, repositories, tests)
- New methods in existing files
- Modifications to existing code
- Configuration changes

### Step 3.2: Group by Responsibility

Group deliverables by responsibility (reason to change):

```
Responsibility: Point balance inquiry
- Add getAvailableBalance() in Point.kt
- Add PointTest for balance query

Responsibility: Point partial usage
- Modify use() in Point.kt
- Modify usePoint() in PointService.kt
- Update PaymentFacade
- Update all tests

Responsibility: Usage history recording
- Add PointUsageHistory entity
- Add PointUsageHistoryRepository
- Integrate with PointService
```

Each responsibility group becomes a milestone.

### Step 3.3: Order Milestones by Dependency

Order so each milestone only depends on previous milestones:

1. Foundational entities first
2. Repositories/persistence second
3. Service layer third
4. Integration/facades last

### Step 3.4: Add References to Each TODO

For each TODO:

1. Find the spec section that defines the requirement
2. Find existing code that shows the pattern to follow
3. Add both references

```markdown
- [ ] Add `use(amount: Long)` in `domain/point/Point.kt` 
      - (spec: point-spec.md#2.3.1, pattern: `domain/coupon/Coupon.kt:L45-60`)
```

### Step 3.5: Identify Affected Files (Check Section)

For each milestone:

1. List files that call/use modified code
2. List tests that verify modified behavior
3. Consider transitive dependencies

Use grep or research.md to find callers:
```bash
grep -r "usePoint" --include="*.kt" .
```

### Step 3.6: Define Tests

For each milestone:

1. What new tests verify the new behavior?
2. What existing tests need updates?
3. Map tests to spec requirements

### Step 3.7: Define Completion Criteria

For each milestone:

1. What test commands should pass?
2. What can be verified via grep/command?
3. What spec requirements are satisfied?

Make criteria executable:
```markdown
### Done When
- [ ] `./gradlew test --tests "*PointTest"` passes
- [ ] `./gradlew test --tests "*PointServiceIntegrationTest"` passes
```

### Phase 3 Checklist

- [ ] All deliverables listed
- [ ] Grouped by responsibility
- [ ] Ordered by dependency
- [ ] Each TODO has spec + pattern reference
- [ ] Check sections include all affected files
- [ ] Tests defined for each milestone
- [ ] Done When criteria are executable

---

## Phase 4: Review and Finalize

### Step 4.1: Green State Check

**CRITICAL**: For each milestone, verify:

- [ ] Does it compile on its own?
- [ ] Do all tests pass after this milestone?
- [ ] Are there any runtime failures (Bean conflicts, DI issues)?
- [ ] Are atomic operations kept together?

**Signature Changes**: If any `Modify[signature]`, verify ALL callers are either:
- Updated in TODO section of same milestone, OR
- Listed in Check section for impact awareness

**Atomic Operations**: Verify none of these are split:
- Interface replacement (add new + delete old)
- Schema + entity changes
- Required parameter additions + all call sites

### Step 4.2: Completeness Check

- [ ] Does every spec requirement map to a milestone?
- [ ] Does every milestone have clear completion criteria?
- [ ] Is anything from research.md missed?

Create mapping table:
```markdown
## Spec Requirement Mapping

| Requirement | Spec Location | Milestone |
|-------------|---------------|-----------|
| Point usage | point-spec.md#2.3 | Milestone 2 |
| Usage history | point-spec.md#3.3 | Milestone 3, 4 |
```

### Step 4.3: Isolation Check

For each milestone, ask: "Could implementer execute this with ONLY the information in this milestone?"

- [ ] File paths present and complete?
- [ ] Spec references present?
- [ ] Pattern references present?
- [ ] Dependencies from previous milestones noted?

### Step 4.4: Risk Assessment

For each milestone:

1. Identify highest-risk item
2. Assign overall risk level
3. Add reason

```markdown
### Risk
- **Level**: 🟠 High
- **Reason**: Signature change to usePoint() affects PaymentFacade and RefundService
```

### Step 4.5: Write plan.md

Assemble everything using `templates/plan-template.md`.

### Phase 4 Checklist

**Green State**
- [ ] Each milestone leaves codebase compilable
- [ ] Each milestone leaves all tests passing
- [ ] Atomic operations NOT split across milestones
- [ ] `Modify[signature]` changes include ALL callers in same milestone or Check

**Completeness**
- [ ] Every spec requirement maps to at least one milestone
- [ ] Every TODO has: file path + spec reference + pattern reference
- [ ] Every TODO uses correct action type
- [ ] Spec Requirement Mapping table complete

**Self-Containment**
- [ ] Each milestone understandable in isolation
- [ ] Dependencies between milestones explicit
- [ ] Clarifications noted where decisions needed

**Executability**
- [ ] Done When criteria are verifiable (commands, tests)
- [ ] Pattern references point to actual existing code
- [ ] File paths follow project conventions

---

## Phase 5: Validate Plan

### Step 5.1: Invoke Validator

If plan-validator sub-agent is available:

```
Validate the plan.

Plan: plan.md
```

### Step 5.2: Handle Validation Result

**If PASSED**: Plan is ready. Report completion to user.

**If FAILED**: Read the validation report and:
1. Identify specific issues
2. Revise plan.md to fix each issue
3. Re-invoke validator

### Step 5.3: Handle Repeated Failures

**CRITICAL: Loop limit is 3 attempts.**

If validation fails 3 times consecutively:
1. STOP - do not continue trying
2. Report to user with:
   - Issues that remain unfixed
   - What you've tried
   - What you need (clarification, access, etc.)

```markdown
## Validation Failed After 3 Attempts

### Remaining Issues
1. [Issue description]
2. [Issue description]

### What I Tried
- [Attempt 1 description]
- [Attempt 2 description]

### What I Need
- [Clarification/access/information needed]
```

### Phase 5 Checklist

- [ ] Validator invoked
- [ ] PASSED: Report completion
- [ ] FAILED: Revise and retry (max 3x)
- [ ] Still failing after 3x: STOP and report

---

## Quick Reference: Phase Outputs

| Phase | Output |
|-------|--------|
| Phase 1 | Understanding of requirements and codebase |
| Phase 2 | List of clarifications needing answers |
| Phase 3 | Draft milestones with all sections |
| Phase 4 | Finalized plan.md ready for validation |
| Phase 5 | Validated plan.md or failure report |
