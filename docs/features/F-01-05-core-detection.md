# F-01 to F-05: core detection (plan)

Status: implemented. The brief was the approved plan.

## Scope

In: event ingest, both detections, alerts and risk score, rules with live invalidation, event generator, Compose stack, CI.

Out, kept for later (see `SPEC.md`): Redis Streams adapter, C# parity test, Azure deployment, group-change rule, and any UI (the role's requirements do not call for one).

## API changes

New contract in `api/openapi.yaml`: `POST /events`, `GET /alerts`, `GET /users/{upn}/risk`, `GET /rules`, `PUT /rules/{ruleId}`.

`RuleConfig` is a free-form object in the contract and validated per rule on the server. A `oneOf` would document it more tightly, but it complicates generated code for three small records (KISS).

## Schema changes

Postgres: `detection_rule` (with notify trigger) and `alert`. ClickHouse: `win_events`, `user_logon_baseline` and its materialised view.

## Domain rules and edge cases

- Window edge: a failure exactly `windowMinutes` before the logon counts.
- A success resets the failure count, so a second logon does not alert again. Trade-off: a genuine logon in the middle of an attack can mask it (see README limitations).
- Out-of-order events within a batch: the whole batch is stored first, then logons are evaluated in event-time order.
- Late events across batches: see ADR 0004.
- Old events (over 24 hours) build the baseline only.
- Baseline excludes the current day, so an event cannot vouch for itself.
- New users are not judged until they have `minBaselineLogons` logons.
- Redelivery: event inserts, baseline counts and alerts are all idempotent.
- Case differences in UPN and host are normalised.
- Malformed Kafka records are skipped; store outages retry for as long as they last.

## Test plan

Unit (with fakes): `WinEventTest`, `RuleCatalogTest`, `BruteForceRuleTest`, `OffHoursRuleTest`, `NewHostRuleTest`, `RiskServiceTest`, `EventProcessorTest`, and `@WebMvcTest` for each controller.

Integration (Testcontainers): `ClickHouseStoresIT`, `PostgresStoresIT`, `InsiderSignalEndToEndIT`.

## Design

`detect` holds the rules and owns the interfaces it needs (`EventStore`, `BaselineStore`, `AlertStore`, `RuleStore`), with no Spring, Kafka or JDBC dependency. `store` and `ingest` implement them; `config` wires them together. `EventProcessor` takes `List<LogonRule>`, so a new rule does not change it. `EventStream` has one implementation today, justified by testing the controller without Kafka and by the planned Redis adapter.

## New dependencies

`spring-kafka` (event stream), `clickhouse-jdbc` (event store), `caffeine` (rule cache), `openapi-generator-maven-plugin` (contract first), Testcontainers modules for each backend. The Postgres driver is at compile scope for `PGConnection.getNotifications()`.

## Risks and open questions

Each logon runs up to three ClickHouse queries, which will not hold at real volume. Batching them per consumer poll is the next performance step; it is left out until a load test shows the need.
