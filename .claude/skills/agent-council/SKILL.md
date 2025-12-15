---
name: agent-council
description: Advisory council that provides multiple AI perspectives on decisions. Use when facing trade-offs, subjective judgments, uncertain decisions, or when diverse viewpoints and opinions would improve judgment quality. Triggers include "council", "ask other AIs", "get different perspectives", "what do others think", or requests for multiple opinions.
---

## Overview

Agent Council acts as an advisory body. When facing uncertain decisions or trade-offs, consult the council to gather
diverse perspectives.

Council provides opinions. The caller makes the final decision.

## Process

1. Encounter uncertain decision point
2. Call council with rich context + specific question
3. Council members provide independent opinions
4. Chairman (Claude) synthesizes opinions into structured advisory
5. Receive advisory and make informed decision

## Context Synchronization

Council members do not share the caller's session context. The caller must explicitly provide:

- **Evaluation Criteria**: Key principles from the review/validation rules
- **Project Context**: Conventions and patterns discovered during session
- **Target Content**: Code, spec, or artifact under review
- **Specific Question**: Points where judgment is needed

> Include context richly. Council members should judge with the same context as the caller.

## How to Call

Execute `scripts/council.sh` from this skill directory:

> Note: Always write the council prompt in English for consistent cross-model communication.

```bash
scripts/council.sh --stdin <<'EOF'
## Evaluation Criteria
[Key principles - in English]

## Project Context
[Conventions and patterns - in English]

## Target
[Code or content under review]

## Question
[Specific points needing judgment - in English]
EOF
```

## Advisory Output Format

Chairman synthesizes council opinions into:

```markdown
## Council Advisory

### Consensus

[Points where council members agree]

### Divergence

[Points where opinions differ + summary of each position]

### Recommendation

[Synthesized advice based on above]
```

## Result Utilization

**Strong Consensus** → Adopt recommendation with confidence

**Clear Divergence** → Options:

- Flag as "Clarification Needed"
- Choose majority position, noting dissent
- Use divergence to identify edge cases

**Mixed Signals** → Weigh perspectives based on relevance