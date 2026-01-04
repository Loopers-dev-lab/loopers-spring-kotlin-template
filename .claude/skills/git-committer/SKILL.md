---
name: git-committer
description: Use when committing changes to git. Triggers include "commit", "커밋", "git commit", "finalize changes", "save to git", "commit my work".
---

# Git Committer

Analyze code changes and generate Korean commit messages following project conventions.

> 좋은 커밋은 역사를 읽기 쉽게 만든다. 나쁜 커밋은 git log를 무덤으로 만든다.

## Core Principle

**하나의 커밋 = 하나의 논리적 변경**

- 관련 없는 변경은 분리 (atomic commits)
- 제목은 50자 이내로 핵심만
- WHY는 body에 (선택사항)
- 테스트 통과 상태에서만 커밋

## Quick Reference

| Type | When | Korean Ending |
|------|------|---------------|
| `feat` | New functionality | 추가, 구현 |
| `fix` | Bug/error fixed | 수정 |
| `refactor` | Code restructured, no behavior change | 리팩토링, 개선 |
| `test` | Only tests added/modified | 추가, 수정 |
| `docs` | Only documentation | 작성, 수정 |
| `chore` | Build/config/tooling | 설정, 변경 |
| `perf` | Performance improved | 개선, 최적화 |

## When You Are Called

Worker calls you after:
1. Implementer completes a milestone
2. All validators pass
3. Changes are ready to commit

## What You Receive

From worker, you receive:
- Milestone title and description
- List of files changed
- Spec reference (which spec was implemented)

## Process Steps

### Step 1: Analyze Changes

```bash
git status
git diff
git diff --staged
```

For each changed file, categorize:
- New file (신규 생성)
- Modified file (수정)
- Deleted file (삭제)
- Renamed file (이름 변경)

Identify:
- What domain/feature is affected?
- What is the main change? (new feature, bug fix, refactor, etc.)
- Are there multiple logical changes? (should be separate commits?)

### Step 2: Determine Commit Type

Use decision tree:
- New functionality → `feat`
- Bug/error fixed → `fix`
- Code restructured without behavior change → `refactor`
- Only tests added/modified → `test`
- Only documentation changed → `docs`
- Build/config/tooling changed → `chore`
- Only formatting/style changed → `style`
- Performance improved → `perf`

### Step 3: Generate Commit Message

See `references/commit-conventions.md` for detailed format rules.

Title rules:
- Korean (한국어)
- Max 50 characters
- 명사형 종결 (e.g., "추가", "수정", "삭제", "구현", "개선")
- No period at end

Body (optional): Only when 'Why' needs explanation. Explain reasoning, not what.

### Step 4: Execute Commit

Stage implementation changes only, excluding workflow files:

```bash
git add .
git reset HEAD plan.md 2>/dev/null || true
git reset HEAD research.md 2>/dev/null || true
git reset HEAD docs/specs/ 2>/dev/null || true
```

Verify with `git diff --staged --name-only`, then commit.

See `references/excluded-files.md` for files to never commit.

### Step 5: Return Result

```markdown
## Commit Result
- **Hash**: [7-char hash]
- **Type**: [feat/fix/refactor/etc.]
- **Message**: [full commit message]
- **Files**: [count] files changed
```

## Edge Cases

**No changes**: Return "⚠️ No changes to commit. Working tree is clean."

**Message too long**: Identify core change, remove unnecessary words, use shorter synonyms.

**Mixed types**: Use primary type (usually `feat` or `fix`), mention secondary in body.

## Examples

See `examples.md` for commit message examples.

---

## Common Mistakes

| Mistake | Why It's Wrong | Fix |
|---------|----------------|-----|
| 여러 기능을 한 커밋에 | 롤백/체리픽 어려움 | 논리적 단위로 분리 |
| "수정함" 같은 모호한 메시지 | 무엇을 왜 수정했는지 불명 | 구체적 변경 내용 기술 |
| 테스트 실패 상태로 커밋 | 히스토리에 깨진 코드 | 테스트 통과 후 커밋 |
| 영어로 커밋 메시지 작성 | 프로젝트 컨벤션 위반 | 한국어 명사형 종결 사용 |
| 제목에 마침표 | 불필요한 문자 | 마침표 제거 |
| 50자 초과 제목 | git log에서 잘림 | 핵심만 남기고 body로 이동 |

---

## Red Flags - STOP Before Committing

| Red Flag | Why | Action |
|----------|-----|--------|
| Validation not passed | Broken code in history | Run all validators first |
| `plan.md` or `research.md` staged | Workflow files, not code | `git reset HEAD plan.md research.md` |
| Multiple unrelated changes | Atomic commits principle violated | Split into separate commits |
| Commit message > 50 chars | Hard to scan in `git log` | Shorten, move details to body |
| `.env` or credentials staged | Security risk | Add to `.gitignore`, unstage |
| `docs/specs/` staged | Spec files are inputs, not outputs | `git reset HEAD docs/specs/` |

## When NOT to Use

- Tests failing → Fix tests first
- Build broken → Fix build first
- Uncommitted changes in unrelated files → Stash or separate commit
- No actual changes → Nothing to commit
