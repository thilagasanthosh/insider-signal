-- Applied at startup by ClickHouseConfig. Every statement must be idempotent.

-- Raw events. ReplacingMergeTree collapses redelivered events with the same sorting key; reads use
-- FINAL so duplicates are invisible before a background merge happens.
CREATE TABLE IF NOT EXISTS win_events (
    event_id     String,
    event_code   UInt16,
    occurred_at  DateTime64(3, 'UTC'),
    upn          String,
    host         String,
    target_group Nullable(String)
)
ENGINE = ReplacingMergeTree
PARTITION BY toYYYYMM(occurred_at)
ORDER BY (upn, occurred_at, event_id);

-- Per-user hourly baseline of successful logons. Counting distinct event ids keeps the count
-- exact when an event is redelivered, because the view sees every insert.
CREATE TABLE IF NOT EXISTS user_logon_baseline (
    upn    String,
    day    Date,
    hour   UInt8,
    host   String,
    logons AggregateFunction(uniqExact, String)
)
ENGINE = AggregatingMergeTree
PARTITION BY toYYYYMM(day)
ORDER BY (upn, day, hour, host);

CREATE MATERIALIZED VIEW IF NOT EXISTS user_logon_baseline_mv TO user_logon_baseline AS
SELECT
    upn,
    toDate(occurred_at) AS day,
    toHour(occurred_at) AS hour,
    host,
    uniqExactState(event_id) AS logons
FROM win_events
WHERE event_code = 4624
GROUP BY upn, day, hour, host;
