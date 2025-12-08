---
description: Create implementation plan from research.md and spec documents
---

Run the planner agent to create plan.md from research findings and specifications.

Planner will:

1. Read research.md for codebase analysis (integration points, patterns, references)
2. Read spec documents in docs/specs/ for business requirements
3. Make infrastructure decisions (locking, retry, timeout, etc.)
4. Create milestones based on responsibilities and package boundaries
5. Add spec references and pattern references to each TODO
6. Output plan.md in project root

Prerequisites:

- research.md exists in project root
- spec documents exist in docs/specs/

Each milestone in plan.md will be:

- One responsibility within one package
- Self-contained with file paths, spec refs, pattern refs
- Executable by implementer in isolation

After plan.md is created, review it before running `/project:work`.