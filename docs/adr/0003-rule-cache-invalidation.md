# ADR 0003: Rule cache invalidation through Postgres LISTEN/NOTIFY

- Status: Accepted
- Date: 2026-11-02

## Context

Every logon reads rule configuration, so rules are cached in-process with Caffeine. A change through any instance must reach all instances without a restart. The options were Postgres `LISTEN/NOTIFY` and a compacted Kafka topic.

## Decision

- A trigger on `detection_rule` calls `pg_notify`. Each instance holds one connection in `LISTEN` and evicts the named rule.
- Reconnecting evicts everything, because notifications sent while disconnected are lost.
- A 10-minute TTL bounds staleness if anything else goes wrong.
- The instance handling a `PUT` also evicts directly, so its next event uses the new rule immediately.

## Consequences

- `NOTIFY` is transactional: it is delivered only if the update commits. A Kafka topic would be a dual write, where a crash between commit and publish leaves caches stale. The trigger also catches changes made directly in SQL.
- Each instance permanently uses one Postgres connection from its pool.
- `NOTIFY` does not suit very high rates, but rule changes are rare.
- This eviction is asynchronous: other instances may score events under the old rule for a few milliseconds. That is acceptable for tuning thresholds.
