## Summary

<!-- What does this PR change, and why? -->

Feature: F-xx
Plan: docs/features/F-xx-<name>.md

## Changes

-

## How it was tested

- [ ] Unit tests
- [ ] Integration tests (Testcontainers), including `@Tag("F-xx")` where the acceptance criteria apply
- [ ] Demo steps in the README still work

## AI assistance

<!-- How was AI tooling used in this PR? Tick all that apply and add a line of detail. -->

- [ ] Plan drafted with AI, reviewed and edited by me
- [ ] Implementation via AI-driven TDD cycles, each diff reviewed
- [ ] AI pre-review of this diff (`/review-pr`); findings addressed below
- [ ] Debugging / root-cause analysis with AI
- [ ] No AI used

AI review findings and what I did about them:

-

## Checklist

- [ ] Java formatted (`fmt-maven-plugin`) and `mvn verify` passes
- [ ] `api/openapi.yaml` changed first if the API changed
- [ ] Design follows SOLID, DRY, KISS and YAGNI
- [ ] Times use the injected `Clock`; hours and days are UTC
- [ ] Reprocessing stays idempotent (events and alerts)
- [ ] Schema changes are new Flyway migrations, or idempotent ClickHouse DDL
- [ ] No secrets or real personal data
- [ ] README / ADRs updated if needed
