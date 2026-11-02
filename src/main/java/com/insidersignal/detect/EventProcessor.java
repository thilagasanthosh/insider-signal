package com.insidersignal.detect;

import com.insidersignal.event.WinEvent;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Stores a batch of events, then evaluates its recent logons against every rule. Storing first lets
 * rules see the whole batch. Both steps are idempotent, so a redelivered batch is safe.
 */
public class EventProcessor {

  private final EventStore events;
  private final AlertStore alerts;
  private final List<LogonRule> rules;
  private final Clock clock;
  private final Duration maxEventAge;

  public EventProcessor(
      EventStore events,
      AlertStore alerts,
      List<LogonRule> rules,
      Clock clock,
      Duration maxEventAge) {
    this.events = events;
    this.alerts = alerts;
    this.rules = rules;
    this.clock = clock;
    this.maxEventAge = maxEventAge;
  }

  public void process(List<WinEvent> batch) {
    events.saveAll(batch);
    var cutoff = clock.instant().minus(maxEventAge);
    batch.stream()
        .filter(WinEvent::isSuccessfulLogon)
        .filter(event -> !event.occurredAt().isBefore(cutoff))
        .sorted(Comparator.comparing(WinEvent::occurredAt))
        .forEach(this::evaluate);
  }

  private void evaluate(WinEvent logon) {
    rules.forEach(rule -> rule.evaluate(logon).ifPresent(alerts::save));
  }
}
