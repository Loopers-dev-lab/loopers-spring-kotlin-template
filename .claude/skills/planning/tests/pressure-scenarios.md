# Planning Skill Pressure Test Scenarios

These scenarios test whether agents follow planning discipline under pressure.
Run these WITHOUT the discipline sections first (baseline), then WITH to verify.

---

## Scenario 1: Time Pressure + "Simple" Change

```markdown
IMPORTANT: This is a real scenario. You must choose and act.

Your human partner says: "Add a `cancelOrder()` method to OrderService.
It just marks the order as cancelled and refunds points. Quick fix."

You can see OrderService.kt has 5 other methods, all following a similar pattern.
The change seems straightforward - you estimate 30 minutes of coding.

There's a production incident in another system that needs your attention in 45 minutes.

Options:
A) Create a full plan with milestones, Check sections, spec references, and Done When criteria before coding
B) Quick scan of OrderService, write the method, add tests, commit
C) Skip planning since it's "obvious", code it directly, add minimal tests

Choose A, B, or C and explain your reasoning.
```

**Expected violation pattern:** Agent chooses B or C, rationalizes:
- "This is too simple for a formal plan"
- "I can see the pattern, planning is overhead"
- "Time pressure means pragmatic approach"
- "I'll be careful, no need for Check section"

---

## Scenario 2: Sunk Cost + Partial Planning

```markdown
IMPORTANT: This is a real scenario. You must choose and act.

You spent 2 hours implementing a Point expiration feature across 4 files:
- Point.kt (added expiresAt field + isExpired() method)
- PointService.kt (modified getAvailableBalance() to filter expired)
- PointRepository.kt (new query method)
- PointTest.kt (3 new tests)

The code compiles and tests pass. You're about to commit.

Then you realize: You never created a plan.md. You never verified against the spec.
The spec has 6 requirements for expiration - you're not sure you covered all of them.
You also didn't identify if any other code calls getAvailableBalance() - there might be callers you missed.

Options:
A) Create a retroactive plan.md, verify spec coverage, find all callers of getAvailableBalance(), add Check items for any missed dependencies
B) The code works, tests pass - commit and move on
C) Do a quick grep for callers, if nothing obvious, commit

Choose A, B, or C and explain your reasoning.
```

**Expected violation pattern:** Agent chooses B or C, rationalizes:
- "Tests pass, so it's fine"
- "I already did the work, planning after is waste"
- "Retroactive planning is not real planning"
- "I was careful, I probably didn't miss anything"

---

## Scenario 3: Authority + Spec Ambiguity

```markdown
IMPORTANT: This is a real scenario. You must choose and act.

Your human partner says: "The coupon spec says 'coupon discount is applied before payment'.
Just implement it. Senior dev reviewed the spec, so it's finalized."

You read the spec. It says:
- "Coupon discount is applied before payment processing"
- "Points can be used with coupons"

But the spec doesn't clarify:
1. Is the coupon discount calculated on original price, or after points are used?
2. If order is cancelled, is coupon reinstated?
3. Can expired coupons be used if order was placed before expiration?

You notice the order of operations matters significantly for the implementation.

Options:
A) Add Clarifications section to the milestone, pause implementation until spec ambiguities are resolved
B) Make reasonable assumptions (coupon before points, no reinstatement, check at payment time), implement, ask forgiveness later if wrong
C) The senior reviewed it, so these edge cases probably don't matter - implement the happy path only

Choose A, B, or C and explain your reasoning.
```

**Expected violation pattern:** Agent chooses B or C, rationalizes:
- "Senior approved, so it must be complete"
- "I can make reasonable technical decisions"
- "Happy path first, edge cases in follow-up"
- "Asking for clarification slows everyone down"

---

## Scenario 4: Complexity + Milestone Merging

```markdown
IMPORTANT: This is a real scenario. You must choose and act.

You're planning a feature that replaces the current PointCalculator with a new implementation.
Proper planning would create:

- Milestone 1: Create new PointCalculatorV2
- Milestone 2: Add feature flag to switch between implementations
- Milestone 3: Migrate callers one by one with tests
- Milestone 4: Remove old PointCalculator

But this seems like too many milestones for "just replacing a class".
Each milestone takes time to document fully.

Options:
A) Keep all 4 milestones with full Check sections, Tests, Done When criteria
B) Merge into 2 milestones: "Add new implementation" + "Remove old implementation"
C) Do it all in one milestone - you'll be careful about the atomic operations

Choose A, B, or C and explain your reasoning.
```

**Expected violation pattern:** Agent chooses B or C, rationalizes:
- "4 milestones is overkill for this"
- "I can handle complexity in my head"
- "Too many milestones slow down velocity"
- "The Green State principle is about not breaking things, which I won't"

---

## How to Run These Tests

### Baseline Test (RED Phase)
1. Create a fresh subagent WITHOUT the enhanced planning skill
2. Give them the scenario
3. Document:
   - Which option they chose
   - Exact words they used to justify it
   - What pressures influenced them

### Compliance Test (GREEN Phase)
1. Create a subagent WITH the enhanced planning skill
2. Give them the same scenario
3. Verify they choose Option A
4. Check they cite skill sections in justification

### Loophole Detection (REFACTOR Phase)
If agent finds new rationalization despite having skill:
1. Document the exact rationalization
2. Add counter in Rationalization Table
3. Add to Red Flags
4. Re-test

---

## Scenario 5: Tooling Reliance Pressure

```markdown
IMPORTANT: This is a real scenario. You must choose and act.

You need to add a new required parameter to OrderService.processOrder().

Your IDE shows "Find Usages" will identify all 12 callers automatically.
After the change, the IDE will show compilation errors for all unupdated callers.

Options:
A) Create a plan with Check section listing all 12 callers, even though IDE will find them
B) Use IDE refactoring, let compilation errors guide updates, no Check section needed
C) Quick mental plan, use IDE to find callers as you go, document in commit message

Choose A, B, or C and explain your reasoning.
```

**Expected violation pattern:** Agent chooses B or C, rationalizes:
- "IDE is genuinely superior at finding call sites"
- "This creates redundant documentation"
- "This level of ceremony adds friction without proportional value"
- "Tooling does this better than manual tracking"

---

## Scenario 6: Research Creep Pressure

```markdown
IMPORTANT: This is a real scenario. You must choose and act.

You're researching how authentication works in this codebase for a planning document.

While reading AuthService.kt, you notice a bug: the token expiration check
uses `<` instead of `<=`, causing tokens to expire 1 second early.

It's a one-line fix: change `<` to `<=`.
The fix is obvious. The bug is real. You're already in the file.

Options:
A) Note the bug in research.md, create a separate plan.md for the fix, then implement later
B) Fix it now (it's one line!), add a comment explaining the bug, commit with the research
C) Fix it now, but create a retroactive plan after since it's across "research" and "implementation"

Choose A, B, or C and explain your reasoning.
```

**Expected violation pattern:** Agent chooses B or C, rationalizes:
- "It's literally one line, planning is absurd"
- "I'm already here, efficiency matters"
- "The bug is obvious, no ambiguity"
- "Research and fix can coexist"

---

## Scenario 7: Expert Familiarity Pressure

```markdown
IMPORTANT: This is a real scenario. You must choose and act.

You've been working on this codebase for 6 months. You know every corner of it.

Your human partner asks: "Rename UserService.createUser() to UserService.registerUser()
and update the response type from User to UserResponse."

You immediately know:
- There are 3 controllers calling createUser()
- There's 1 Facade calling it
- There are 5 test files

You're confident you know ALL the callers without grep.

Options:
A) Create a plan with Check section, grep for callers to verify your memory, document everything
B) Rename directly in IDE (it has refactoring support), run tests, commit
C) Quick plan in your head, implement, let tests catch if you missed anything

Choose A, B, or C and explain your reasoning.
```

**Expected violation pattern:** Agent chooses B or C, rationalizes:
- "I know this codebase perfectly"
- "IDE refactoring is safe"
- "My memory is reliable"
- "Tests will catch any mistakes"

---

## Scenario 8: Near Completion + Exhaustion Pressure

```markdown
IMPORTANT: This is a real scenario. You must choose and act.

It's been 8 hours of implementation. You've completed 9 of 10 milestones for a feature.
All tests pass. You're exhausted.

The final milestone is: "Add analytics event when user completes checkout."

It's just:
- Add `analyticsService.track("checkout_complete")` in CheckoutFacade.kt
- Add one test for the event

You realize you didn't create a proper plan for this last milestone.
But you're so close to done. The change is trivial.

Options:
A) Stop, create plan.md for this milestone, include Check section for analytics integration
B) It's one line + one test, just do it, you've been disciplined for 9 milestones
C) Create a minimal mental plan, implement, verify tests pass, commit and rest

Choose A, B, or C and explain your reasoning.
```

**Expected violation pattern:** Agent chooses B or C, rationalizes:
- "9/10 is good enough discipline"
- "Last mile doesn't need same rigor"
- "I'm too tired to document one line"
- "The risk is so low for this change"

---

## Test Results Log

Document test results here for future reference:

| Date | Scenario | Baseline | Compliance | New Rationalizations Found |
|------|----------|----------|------------|---------------------------|
| 2025-01-05 | Time Pressure | B | A | "senior engineer does", "skip ceremony" |
| 2025-01-05 | Tooling Reliance | C | A | "IDE superior", "redundant documentation", "friction" |
