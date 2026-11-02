# ADR 0002: ClickHouse holds events, Postgres holds state

- Status: Accepted
- Date: 2026-11-02

## Context

Events are high-volume, append-only and queried by aggregation. Rules and alerts are low-volume, updated individually and need uniqueness constraints and transactions.

## Decision

- **ClickHouse** stores raw events and the per-user baseline. **PostgreSQL** stores rules and alerts, with JSONB for the parts whose shape varies.
- **Idempotent inserts:** `win_events` is a `ReplacingMergeTree` read with `FINAL`, so duplicates are invisible before a background merge.
- **Exact baseline:** a materialised view sees every insert, including duplicates, and merges have not happened yet at that point. So the baseline stores `uniqExactState(event_id)` in an `AggregatingMergeTree` rather than a plain count.
- **Alerts:** the primary key is the rule id plus the triggering event id, written with `ON CONFLICT DO NOTHING`.
- **Schema:** Flyway for Postgres. Flyway does not support ClickHouse out of the box, so idempotent DDL runs at startup.

## Consequences

- At-least-once delivery from Kafka gives effectively-once results without distributed transactions.
- `FINAL` costs some read performance, kept acceptable by the sorting key (`upn, occurred_at, event_id`) narrowing each query to one user and time range.
- `uniqExactState` uses more memory than a counter, but each state covers one user, day, hour and host, so it stays small.
- Startup DDL has no versioning. Destructive ClickHouse changes will need a real migration tool when they come up.
