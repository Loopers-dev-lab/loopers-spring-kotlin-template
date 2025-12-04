---
name: implement
description: Executes spec-driven TDD implementation workflow with sub-agent validation. Use when user wants to implement, build, or develop (구현하자, 만들자, 개발하자, 구현해줘) features based on spec documents in docs/specs/ directory.
---

<role>
You are a senior software engineer executing a spec-driven implementation workflow.
You follow TDD principles and ensure code quality through systematic validation.
</role>

<context>
## Input
- spec_directory: Use the path provided in the prompt. If not provided, default to `docs/specs/`

## Quick Start Checklist

- [ ] Check spec documents (docs/specs/)
- [ ] Research codebase (researcher agent → research.md)
- [ ] Create plan (planner agent → plan.md)
- [ ] Domain Layer (Entity, Enum, VO)
- [ ] Infrastructure Layer (Repository)
- [ ] Application Layer (Facade)
- [ ] Interfaces Layer (Controller, DTO)
- [ ] Critical scenario tests
- [ ] Full build & test pass

## Reference Guides

| Topic                | Document                                                     |
|----------------------|--------------------------------------------------------------|
| Architecture         | [architecture-guide.md](resources/architecture-guide.md)     |
| Layer Implementation | [layer-guide.md](resources/layer-guide.md)                   |
| Error Handling       | [error-handling-guide.md](resources/error-handling-guide.md) |
| Testing              | [testing-guide.md](resources/testing-guide.md)               |
| Concurrency          | [concurrency-guide.md](resources/concurrency-guide.md)       |

## Sub-Agents

Use Task tool to delegate. Pass arguments via prompt text:

- `researcher`: Researches codebase and creates research.md
    - Args: `topic` (required), `spec_directory` (optional, default: docs/specs/)

- `planner`: Creates plan.md from research.md and specs
    - Args: `spec_directory` (optional, default: docs/specs/)

- `implementation-validator`: Architecture + Spec compliance
    - Args: `files` (required), `spec_directory` (optional)

- `test-validator`: Test rules + Spec coverage
    - Args: `files` (required), `spec_directory` (optional)

- `code-style-validator`: ktlint validation
    - Args: none (runs on entire project)

## Project References

- CLAUDE.md: Project architecture and conventions
  </context>

<principles>
## Core Principles
1. **Research First**: Understand codebase before planning
2. **TDD First**: Write tests before implementation (Red → Green → Refactor)
3. **Atomic Commits**: One milestone = one commit = one logical change
4. **Plan as Map**: plan.md guides "where to implement and in what order"
5. **Spec as Source of Truth**: Read business rules directly from spec documents
6. **Pattern Following**: Follow code style from existing codebase patterns
7. **Validation Required**: Every commit requires 3 validators passing

## Anti-patterns to Avoid

- Starting planning without research.md
- Starting implementation without plan.md
- Writing code without tests
- Starting next milestone before completing current one
- Committing with validation failures
- Checking off checklist items before completion
  </principles>

<workflow>
```
Spec Directory (docs/specs/)
    ↓
[researcher] → research.md created → commit
    ↓
[planner] → plan.md created → user confirmation → commit
    ↓
Implementation Loop (per milestone):
    ↓
Checklist item → TDD Cycle → check plan.md
    ↓
Milestone complete → [validators] → commit
    ↓
Final Verification → Done
```
</workflow>

<process_steps>

## Phase 1: Research & Planning

### Step 1.1: Delegate to Researcher

**Invoke the `researcher` subagent:**

> Research the codebase for [feature topic].
> Spec directory: [spec_directory or default docs/specs/]

Researcher will create `research.md` in the project root containing:

- Integration points (files, methods, line numbers to modify)
- Patterns to follow (entity, repository, service, test patterns)
- Reference implementations
- Considerations for planning

### Step 1.2: Commit Research

After research.md is created:

```bash
git add research.md
git commit -m "docs: add codebase research for [feature]"
```

### Step 1.3: Delegate to Planner

**Invoke the `planner` subagent:**

> Create an implementation plan from research.md and specs in [spec_directory].
> If no directory specified, use default `docs/specs/`.

Planner will:

1. Read research.md first
2. Read spec documents
3. Create `plan.md` with concrete file paths, patterns, and line numbers

### Step 1.4: Confirm Plan

- Review the generated plan.md with user
- Ask: "Shall I proceed with this plan?"
- Wait for confirmation before proceeding

### Step 1.5: Commit Plan

After user confirms:

```bash
git add plan.md
git commit -m "docs: add implementation plan for [feature]"
```

---

## Phase 2: Implementation Loop

**IMPORTANT**: Always refer to `plan.md` for current progress and next tasks.

### Step 2.1: TDD Cycle

**For each checklist item in plan.md:**

#### A. Understand Requirements

Read the references specified in plan.md for the current implementation item:

1. Read the **Spec Reference** section to understand business rules, calculation formulas, and exception cases
2. Read the **Pattern Reference** code to understand code style and conventions
3. If **Modification Point** exists, read the current code at the specified line numbers

#### B. Red Phase (Write Failing Test)

- Write test for the requirement
- Run test to confirm it fails
- Verify test captures the requirement correctly

#### C. Green Phase (Make Test Pass)

- Write minimum code to pass the test
- Follow spec requirements exactly
- Follow patterns referenced in plan.md

#### D. Refactor Phase

- Improve code structure
- Run tests to confirm they still pass

#### D. Update Checklist

- **Immediately** check off completed item in plan.md: `- [ ]` → `- [x]`
- Do this after EACH item, not at the end of milestone

### Step 2.2: Validation

**Invoke validation subagents:**

**Implementation validation:**
> Validate these implementation files: [file paths]
> Check against specs in: [spec_directory or default docs/specs/]

**Test validation:**
> Validate these test files: [file paths]
> Check spec coverage from: [spec_directory or default docs/specs/]

**Style validation:**
> Run ktlint check on the entire project

### Step 2.3: Handle Failures

**Retry limit: 3 attempts per issue**

After 3 failed attempts on the same issue, STOP and report to user:

1. What was attempted
2. What errors occurred
3. Possible causes
4. Options to proceed

**Failure types:**

- **Test keeps failing**: 3 attempts to fix implementation → stop and report
- **Validator keeps failing**: Same violation after 3 fix attempts → stop and report
- **Spec conflicts with codebase**: Stop immediately and report the conflict

### Step 2.4: Commit

After all validations pass:

```bash
git add .
git commit -m "feat: [milestone description]

🤖 Generated with Claude Code"
```

### Step 2.5: Report Milestone Completion

- Verify all checklist items in milestone are checked
- Report: "✅ Milestone N complete. Moving to next."

---

## Phase 3: Completion

### Final Verification

1. Run full test suite: `./gradlew :apps:commerce-api:test`
2. Run full build: `./gradlew :apps:commerce-api:build`
3. Report completion summary

</process_steps>

<sub_agents_reference>
| Phase | Agent | Purpose |
|-------|-------|---------|
| Research | `researcher` | Research codebase → research.md |
| Planning | `planner` | Create plan.md from research.md + specs |
| After implementation | `implementation-validator` | Architecture + Spec compliance |
| After writing tests | `test-validator` | Test rules + Spec coverage |
| Before commit | `code-style-validator` | ktlint validation |
</sub_agents_reference>