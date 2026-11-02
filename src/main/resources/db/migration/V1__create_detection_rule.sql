CREATE TABLE detection_rule (
    id         TEXT PRIMARY KEY,
    config     JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO detection_rule (id, config) VALUES
    ('brute-force', '{"failureThreshold": 5, "windowMinutes": 5, "score": 60}'),
    ('off-hours',   '{"minBaselineLogons": 20, "minLogonsInHour": 1, "score": 30}'),
    ('new-host',    '{"minBaselineLogons": 20, "score": 30}');

-- Tells every instance to evict its cached rule. NOTIFY is delivered only if the transaction
-- commits, so cache invalidation cannot disagree with the data (ADR 0003).
CREATE FUNCTION notify_rule_changed() RETURNS trigger AS $$
BEGIN
    PERFORM pg_notify('rule_changed', NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER detection_rule_changed
    AFTER UPDATE ON detection_rule
    FOR EACH ROW EXECUTE FUNCTION notify_rule_changed();
