# Implementation Plan: [Feature Name]

**Created**: [Date]
**Specs**: [List of spec files referenced]
**Research**: [research.md path if used]

---

## Milestones Overview

| # | Title | Scope | Risk | Dependencies |
|---|-------|-------|------|--------------|
| 1 | [Title] | [Brief scope] | 🟢 Low | None |
| 2 | [Title] | [Brief scope] | 🟡 Medium | Milestone 1 |
| 3 | [Title] | [Brief scope] | 🟠 High | Milestone 2 |

---

## Milestone Details

- [ ] Milestone 1: [Responsibility Title]

### Risk

- **Level**: 🟢 Low
- **Reason**: [Why this risk level]

### TODO

- [ ] Add `[path]` - [description] (spec: [ref], pattern: `[file:lines]`)
- [ ] Add `[path]` - [description] (spec: [ref])

### Tests

- [ ] Create `[test_path]` - [what to verify]

### Done When

- [ ] `./gradlew test --tests "*TestName*"` passes

---

- [ ] Milestone 2: [Responsibility Title]

### Risk

- **Level**: 🟡 Medium
- **Reason**: [Why this risk level]

### TODO

- [ ] Modify[logic] `[path]` - [description] (spec: [ref], pattern: `[file:lines]`)
- [ ] Modify[signature] `[path]` - [description] (spec: [ref])

### Check

- [ ] Check `[path]` - [why this file might be affected]
- [ ] Check `[path]` - [why this file might be affected]

### Tests

- [ ] Update `[test_path]` - [what to verify]
- [ ] Update `[test_path]` - [what to verify]

### Done When

- [ ] `./gradlew test --tests "*TestName*"` passes
- [ ] `./gradlew test --tests "*TestName*"` passes

---

- [ ] Milestone 3: [Responsibility Title]

### Risk

- **Level**: 🟠 High
- **Reason**: [Why this risk level - e.g., signature change, multiple callers]

### Clarifications

- [ ] [Question that must be answered before implementation]

### TODO

- [ ] Modify[signature] `[path]` - [description] (spec: [ref])
- [ ] Modify `[path]` - [description to update caller] (spec: [ref])
- [ ] Modify `[path]` - [description to update caller] (spec: [ref])

### Check

- [ ] Check `[path]` - [caller of modified method]
- [ ] Check `[path]` - [caller of modified method]
- [ ] Check `[path]` - [test file]

### Tests

- [ ] Update `[test_path]` - [what to verify]
- [ ] Create `[test_path]` - [what to verify]

### Done When

- [ ] `./gradlew test --tests "*TestName*"` passes
- [ ] [Other verifiable criterion]

---

## Spec Requirement Mapping

| Requirement | Spec Location | Milestone |
|-------------|---------------|-----------|
| [Requirement 1] | [file#section] | Milestone 1 |
| [Requirement 2] | [file#section] | Milestone 2 |
| [Requirement 3] | [file#section] | Milestone 2, 3 |

---

## Notes for Worker

- [Any special instructions for execution]
- [Known risks or watch-outs]
- [Order dependencies if not linear]
