---
description: Execute next milestone from plan.md using worker agent
---

Run the worker agent to execute the next unchecked milestone from plan.md.

Worker will:

1. Read plan.md and find the first unchecked milestone
2. Extract that milestone as isolated instructions
3. Delegate to implementer agent
4. Run all validators in sequence (code-style, code-reviewer, architecture-reviewer, test-reviewer, spec-validator)
5. On success: commit via git-committer, update checkbox, report to user
6. On failure: report issues and stop

Use this command to progress through the implementation plan one milestone at a time.

Prerequisites:

- plan.md exists in project root
- research.md exists (for implementer context)
- spec documents exist in docs/specs/

After each milestone, worker stops and waits for your review. Run `/project:work` again to continue to the next
milestone.