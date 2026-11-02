# CLAUDE.md

Instructions for AI coding agents working in this repository. Read this file and `SPEC.md` before starting any task.

## Project

**insider-signal** is a small UEBA service: it ingests Windows security events and flags risky user behaviour. It is an API-only prototype, with no user interface.

- Java 21, Spring Boot 3.5, Spring Kafka, Spring JDBC, Flyway, Caffeine
- Kafka for the event stream, ClickHouse for events and baselines, PostgreSQL 16 for rules and alerts
- `api/openapi.yaml` is the contract. Controllers implement interfaces generated from it
- `SPEC.md` is the source of truth for behaviour. Feature IDs (F-01 … F-09) are used everywhere

## Commands

```bash
# Whole stack (API on http://localhost:8090)
docker compose up -d --build --wait

mvn com.spotify.fmt:fmt-maven-plugin:format   # format Java (run before every commit)
mvn test                                      # unit tests
mvn verify                                    # fmt check + unit + Testcontainers ITs (needs Docker)
mvn verify -Dit.test=InsiderSignalEndToEndIT  # one integration test class
mvn verify -Dgroups=F-02                      # tests for one feature

# Replay traffic against the running stack
java tools/event-generator/EventGenerator.java history
```

## How to work: plan, then TDD

1. **Plan first.** For any feature or non-trivial change, write a plan in `docs/features/F-xx-<name>.md`: scope, API and schema changes, edge cases, and the list of tests you will write. **Stop and wait for approval** before writing code.
2. **Contract first.** If the API changes, change `api/openapi.yaml` before anything else.
3. **Acceptance tests.** Turn the feature's acceptance criteria from `SPEC.md` into integration tests tagged `@Tag("F-xx")`. They should fail until the feature exists.
4. **TDD, strictly.** For every behaviour:
   - **Red:** write one failing test. Run it and confirm it fails for the right reason.
   - **Green:** write the simplest code that makes it pass.
   - **Refactor:** clean up with all tests green.
   - Repeat. Do not write production code without a failing test that needs it.
5. **Verify.** Run the formatter, then `mvn verify`. All must pass.
6. **Self-review** the full diff against the rules below before saying the task is done.

## Design principles

Apply these in every plan, implementation and review. When principles pull in different directions, prefer the simpler option (KISS, YAGNI) and explain the trade-off in the plan.

### SOLID

- **Single Responsibility:** each class has one reason to change. Controllers handle HTTP only, `detect` holds the rules, `store` handles persistence, `ingest` handles the event stream.
- **Open/Closed:** add behaviour by adding code. A new detection is a new `LogonRule` implementation and one bean in `DetectionConfig`; `EventProcessor` does not change.
- **Liskov Substitution:** implementations must honour their interface's contract. Every `EventStore` and `AlertStore` must be idempotent, including test fakes.
- **Interface Segregation:** keep interfaces small and focused (`EventStream`, `EventStore`, `BaselineStore`, `AlertStore`, `RuleStore`, `LogonRule`).
- **Dependency Inversion:** `detect` depends on its own interfaces, not on Kafka, JDBC, Spring or `Instant.now()`. Inject dependencies through constructors (no field injection in production code).

### DRY (Don't Repeat Yourself)

- One source of truth for each rule: the API shape lives in `api/openapi.yaml`, name normalisation in `WinEvent.normalise`, rule settings in their config records.
- Share test data through builders (`aLogon().by(upn).at(time).build()`) and fakes.
- Don't over-apply DRY: `OffHoursRule` and `NewHostRule` look alike but change for different reasons, so they stay separate.

### KISS (Keep It Simple)

- Choose the simplest design that meets the spec and passes the tests. Plain classes and SQL before patterns, frameworks or abstractions.
- No clever code: readable names, short methods, early returns, no deep nesting.
- Prefer standard Spring Boot and library features over custom infrastructure.

### YAGNI (You Aren't Gonna Need It)

- Build only what the current feature in `SPEC.md` needs. No speculative interfaces, configuration options or "future-proofing".
- Don't implement features listed under Later in `SPEC.md`, even partially. **There is no UI, and none should be added.**
- An abstraction needs at least two real uses, or a testing need, before it is introduced.

During `/review-pr`, flag violations of these principles alongside correctness issues.

## Rules

### Must

- Times: use the injected `java.time.Clock`; never call `Instant.now()` or `LocalDate.now()` in production code. Hours of day and baseline days are UTC.
- Idempotency: reprocessing any event must not change a result. Event inserts collapse by event id, the baseline counts distinct event ids, and alert ids combine the rule and the triggering event.
- Consumer order: store a batch before evaluating it, and commit offsets only after both have succeeded.
- Schema: Postgres changes only via new Flyway migrations; never edit an applied migration. ClickHouse DDL in `db/clickhouse/schema.sql` must stay idempotent.
- SQL: write it by hand with bound parameters; never concatenate user input into SQL.
- Errors returned as RFC 7807 `ProblemDetail` with field-level messages.
- Keep `README.md`, `api/openapi.yaml` and ADRs (`docs/adr/`) in sync with changes.

### Must not

- Never commit secrets or real personal data. Use `.env` (git-ignored) and fictional users (`@contoso.example`).
- Never log event payloads at `INFO` or above: they name real people in real deployments.
- Never delete, skip or weaken a test to make the build pass. If a test is wrong, explain why and ask.
- Never push to `main`, merge PRs, or change CI and branch protection without being asked.
- Do not add new dependencies without stating why in the plan.

## Testing conventions

- Unit tests: `*Test.java`, JUnit 5 + AssertJ; one behaviour per test; names describe behaviour (`countsAFailureExactlyAtTheWindowEdge`).
- Integration tests: `*IT.java`, extending `IntegrationTest` (real Postgres, ClickHouse and Kafka through Testcontainers). Use a random UPN per test, because the containers are shared.
- Use `WinEventBuilder` and the in-memory fakes (`FakeEventStore`, `FakeBaselineStore`, `FakeAlertStore`, `FakeRuleStore`) instead of mocking the domain.
- Every acceptance criterion in `SPEC.md` has at least one test. End-to-end ones carry `@Tag("F-xx")`.

## Git conventions

- Branches: `feature/F-06-redis-streams`, `fix/F-02-window-edge`, `chore/<topic>`.
- Conventional Commits: `feat(detect): …`, `fix(ingest): …`, `test(store): …`, `docs: …`.
- Small PRs. Fill in `.github/pull_request_template.md`, including how AI was used.

## Definition of done

- [ ] Plan approved and saved in `docs/features/`
- [ ] `api/openapi.yaml` updated first if the API changed
- [ ] Tests written first; unit and integration tests passing
- [ ] `mvn verify` passes (includes fmt check)
- [ ] Design follows SOLID, DRY, KISS and YAGNI; any trade-off explained in the plan or PR
- [ ] No secrets, no real personal data
- [ ] README, contract and ADRs updated
