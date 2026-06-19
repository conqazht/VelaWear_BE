---
name: review-pr
description: Review VelaWear backend code changes, diffs, branches, or pull requests. Use when asked for review, PR review, code audit, or checking practical architecture fit, security, naming consistency, performance risks, VelaWear project rules, tests, documentation, Spring Boot conventions, and optionally SOLID/Clean Architecture when changed code is complex enough to need those lenses.
---

# Review PR

Use this skill in code-review mode.

## Workflow

1. Inspect the changed files and relevant surrounding code.
2. Read `docs/PROJECT-RULES.md` and relevant feature `CONTEXT.md` when needed.
3. Prioritize bugs, regressions, security issues, practical architecture problems, performance risks, missing tests, and contract violations.
4. Apply optional SOLID or Clean Architecture lenses only when the changed code has matching complexity.
5. Report findings first, ordered by severity, with file and line references.
6. Keep summaries secondary.
7. If no issues are found, say so clearly and mention remaining risk or test gaps.

Read `references/checklist.md` for the review checklist.
