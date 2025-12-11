---
description: Research codebase and external libraries for implementation planning
---

Run the researcher agent to analyze the codebase and create research.md.

Researcher will:

1. Read ALL spec documents in docs/specs/
2. Map the codebase structure and find integration points
3. Find pattern references (entity, repository, service, test)
4. Identify error handling patterns and ErrorType values
5. Research external libraries via Context7 if needed
6. Output research.md in project root

Prerequisites:

- spec documents exist in docs/specs/

---

After research.md is created, run `/project:plan` to create the implementation plan.