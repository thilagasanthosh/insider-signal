---
description: Draft an implementation plan for an insider-signal feature from SPEC.md, then stop for approval
argument-hint: <feature-id, e.g. F-06>
---

Draft an implementation plan for feature $ARGUMENTS.

1. Read `CLAUDE.md` and the $ARGUMENTS section of `SPEC.md`, plus any related existing code.
2. Write the plan to `docs/features/$ARGUMENTS-<short-name>.md` with these sections:
   - **Scope:** what is in and out of this change.
   - **API changes:** changes to `api/openapi.yaml`, including error cases.
   - **Schema changes:** new Flyway migrations and ClickHouse DDL.
   - **Domain rules and edge cases:** list every one (window edges, late and duplicate events, retries, idempotency).
   - **Test plan:** the unit and integration tests you will write, in the order you will write them.
   - **Design:** main classes and interfaces, and how the design follows SOLID, DRY, KISS and YAGNI. Note any trade-off between them.
   - **New dependencies:** with justification, or "none".
   - **Risks and open questions.**
3. Do not write any production or test code.
4. Show me the plan and stop. Wait for my approval or changes.
