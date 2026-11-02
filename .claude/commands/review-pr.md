---
description: AI pre-review of the current branch's changes against CLAUDE.md and SPEC.md before opening a PR
---

Review the changes on the current branch against `main` (`git diff main...HEAD`).

Check, and report findings grouped by severity (must fix / should fix / nit), each with file and line:

1. **Spec:** does the behaviour match the relevant `SPEC.md` feature and its acceptance criteria? Is anything missing?
2. **Contract:** was `api/openapi.yaml` changed first, and do controllers only implement generated interfaces?
3. **Tests:** is every behaviour covered? Any test that does not actually prove the behaviour?
4. **Correctness:** window edges; UTC hours and days; times through the injected `Clock`; case-insensitive names; null handling.
5. **Reliability:** idempotency under redelivery, Kafka offset handling, retry behaviour, late and out-of-order events.
6. **Security and privacy:** no secrets, no real personal data, input validation, safe error messages.
7. **Database:** Flyway migrations only for Postgres; idempotent ClickHouse DDL; indexes and sorting keys that suit new queries.
8. **Design principles:** SOLID (controllers HTTP only, small interfaces, constructor injection, no framework or clock dependencies in `detect`), DRY, KISS, YAGNI.
9. **Conventions:** formatter run, naming, Conventional Commit messages, docs and ADRs updated.

Do not change any code. End with a short list I can paste into the PR template's "AI review findings" section.
