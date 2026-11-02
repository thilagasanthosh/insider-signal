CREATE TABLE alert (
    id          TEXT PRIMARY KEY, -- rule id + triggering event id: makes alerting idempotent
    rule_id     TEXT NOT NULL REFERENCES detection_rule (id),
    upn         TEXT NOT NULL,
    severity    TEXT NOT NULL,
    score       INTEGER NOT NULL CHECK (score >= 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    detail      JSONB NOT NULL   -- {"reason": "...", "evidenceEventIds": ["...", ...]}
);

CREATE INDEX alert_upn_occurred_at_idx ON alert (upn, occurred_at DESC);
CREATE INDEX alert_occurred_at_idx ON alert (occurred_at DESC);
