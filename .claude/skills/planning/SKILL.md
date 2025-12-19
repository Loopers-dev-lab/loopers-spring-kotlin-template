---
name: planning
description: This skill should be used when the user asks to "create a plan", "plan the implementation", "design milestones", "break down into tasks", or needs to transform requirements into executable implementation steps. Provides methodology for creating self-contained, executable milestones.
---

# Planning Skill

Methodology for creating implementation plans with executable, self-contained milestones.

A good plan makes implementation almost mechanical - the implementer just follows the map. A vague plan leads to confusion and rework.

---

## Core Principles

### CRITICAL: Green State Constraint

Each milestone MUST leave codebase in green state:

- Compilable (`./gradlew compileKotlin`)
- All tests pass (`./gradlew test`)

If changing A breaks B, include fixing B in the SAME milestone. This includes test code - if modifying production code breaks existing tests, update those tests in the same milestone.

Runtime constraints matter: Spring Bean conflicts, DI failures, and configuration errors are NOT caught by compilation but will fail tests.

### CRITICAL: Atomic Operations

These MUST NOT be split across milestones:

| Operation | Symptom if Split |
|-----------|------------------|
| Interface implementation replacement | `NoUniqueBeanDefinitionException` |
| Database schema + entity change | `SchemaManagementException` |
| Signature change without updating callers | Compilation failure |
| Required field/parameter addition | Compilation failure in dependents |

### CRITICAL: Change Propagation

When you modify code, changes propagate. Everything affected must be in the SAME milestone.

**Signature Changes → Compilation Failure**

Change method signature, return type, or add required parameter → all callers break.

**Logic Changes → Test Failure**

Change internal logic → existing tests verifying old behavior fail.

**Structural Changes → Runtime Failure**

Replace interface impl, change schema → Spring context or DB mapping fails.

### One Milestone = One Responsibility

A responsibility is a reason to change.

- "Point can be used" → one responsibility
- "Point can expire" → another responsibility
- "Usage history is recorded" → another responsibility

When responsibility is clear, scope becomes clear. Everything needed to fulfill that responsibility goes in the milestone. Nothing else.

### Self-Contained Milestones

Each milestone must be understandable without context from other milestones.

Think of each milestone as a work order that could be handed to a contractor who knows nothing about the rest of the project.

- Implementer may execute milestone without seeing full plan
- All file paths, spec references, pattern references included
- Dependencies on previous milestones explicitly noted

---

## Milestone Structure (Summary)

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
```

For detailed structure, action types, and examples, see `references/milestone-guide.md`

---

## Action Types (Summary)

| Action | When to Use | Risk |
|--------|-------------|------|
| `Create` | New file | 🟢 Low |
| `Add` | New method/field in existing file (no existing code affected) | 🟢 Low |
| `Modify[signature]` | Change parameters/return type | 🟠 High - update all callers |
| `Modify[logic]` | Change implementation, same signature | 🟡 Medium - update tests |
| `Modify[field]` | Add/remove/change fields | 🟡 Medium |
| `Delete` | Remove file or method | 🔴 Critical - verify no usages |

---

## Risk Classification (Summary)

| Level | When to Use |
|-------|-------------|
| 🟢 Low | New files, additive code |
| 🟡 Medium | Modify existing logic, config changes |
| 🟠 High | Signature changes, schema changes |
| 🔴 Critical | Deletions, interface replacement, breaking changes |

**CRITICAL**: 🔴 Critical items require explicit verification before execution.

---

## Quality Checklist (Summary)

Before finalizing any milestone:

**Green State**
- [ ] Milestone leaves codebase compilable
- [ ] Milestone leaves all tests passing
- [ ] Atomic operations NOT split
- [ ] Signature changes include ALL callers

**Completeness**
- [ ] Every TODO has: file path + spec reference + pattern reference
- [ ] Every TODO uses correct action type
- [ ] Files that MAY be affected are in Check section

**Self-Containment**
- [ ] Milestone understandable in isolation
- [ ] Dependencies explicitly noted

For full checklist, see `references/workflow.md#phase-4`

---

## References

| File | When to Read |
|------|--------------|
| `references/milestone-guide.md` | When writing milestone details |
| `references/workflow.md` | When following planning process step-by-step |
| `templates/plan-template.md` | When writing final plan.md output |
| `examples/point-system-plan.md` | For reference on good plan structure |
