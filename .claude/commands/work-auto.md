---
description: Execute milestones continuously until completion or failure
---

Repeat until done:

1. Read plan.md
2. Find the first unchecked milestone
3. If none found → report "All milestones complete" and stop
4. Launch worker agent: "Execute Milestone N"
5. On success → loop back to step 1
6. On failure → stop and report
