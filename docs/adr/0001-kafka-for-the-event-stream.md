# ADR 0001: Kafka for the event stream, behind an interface

- Status: Accepted
- Date: 2026-11-02

## Context

Security telemetry arrives in bursts and has to survive a store outage. The role mentions Redis Streams or Kafka, so the choice needs a reason, and it should stay open to testing.

## Decision

- Use **Kafka**, keyed by UPN, so each user's events stay in order within a partition.
- The REST API returns `202` only after `acks=all` for the whole batch.
- Ingest depends on the `EventStream` interface; Kafka is one implementation.

## Consequences

- Kafka's retention lets us replay history and absorb long outages without memory pressure, which Redis Streams would need careful trimming and capacity planning to match. Redis would be lighter to run and has lower latency, which is why an adapter is worth comparing later (F-06).
- Ordering is per user only. That matches detection, which never compares users.
- A hot user cannot be spread across partitions, but one user's event rate is small.
- The interface currently covers publishing only. A Redis adapter would add its own consumer, as `KafkaEventConsumer` is a thin adapter over `EventProcessor`.
