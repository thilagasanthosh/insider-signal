# ADR 0004: Late and out-of-order events

- Status: Accepted
- Date: 2026-11-02

## Context

Windows event forwarding can delay events by minutes or hours, and they can arrive out of order. Detection should use event time, not arrival time.

## Decision

- Windows and baselines use **event time**.
- The brute-force window is read from ClickHouse rather than held in memory, so it survives restarts and rebalances without any state store.
- Each batch is **stored before evaluation**, and its logons are evaluated in event-time order, so disorder within a batch does not matter.
- Events older than `max-event-age` (24 hours) **build the baseline but raise no alerts**.
- The baseline **excludes the current UTC day**.

## Consequences

- **Known gap:** if the 4624 is processed in an earlier batch than the 4625s it follows, the brute-force rule misses it, because only logons are evaluated. The fix would be a delayed re-evaluation or a watermark (for example re-checking logons a few minutes later). It is left out for now, as it adds scheduling and state.
- Logons on the same day on a new host each alert until the next day, since today is not in the baseline. Alert volume is bounded by logon volume, and analysts see the repeat.
- Replaying history is safe.
