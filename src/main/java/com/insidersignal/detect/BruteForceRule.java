package com.insidersignal.detect;

import com.insidersignal.event.WinEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Repeated failed logons followed by a success: someone probably guessed the password. */
public class BruteForceRule implements LogonRule {

  private final EventStore events;
  private final RuleCatalog rules;

  public BruteForceRule(EventStore events, RuleCatalog rules) {
    this.events = events;
    this.rules = rules;
  }

  @Override
  public Optional<Alert> evaluate(WinEvent logon) {
    var config = rules.config(RuleId.BRUTE_FORCE, BruteForceConfig.class);
    var window = Duration.ofMinutes(config.windowMinutes());
    var attempts =
        events.logonAttempts(logon.upn(), logon.occurredAt().minus(window), logon.occurredAt());
    var failures = failuresSinceLastSuccess(attempts, logon);
    if (failures.size() < config.failureThreshold()) {
      return Optional.empty();
    }
    var reason =
        "%d failed logons within %d minutes, then a successful logon on %s"
            .formatted(failures.size(), config.windowMinutes(), logon.host());
    return Optional.of(
        Alert.forLogon(
            RuleId.BRUTE_FORCE,
            logon,
            Severity.HIGH,
            config.score(),
            reason,
            evidence(failures, logon)));
  }

  private static List<WinEvent> failuresSinceLastSuccess(List<WinEvent> attempts, WinEvent logon) {
    var failures = new ArrayList<WinEvent>();
    for (var attempt : attempts) {
      if (attempt.eventId().equals(logon.eventId())) {
        continue;
      }
      if (attempt.isSuccessfulLogon()) {
        failures.clear();
      } else {
        failures.add(attempt);
      }
    }
    return failures;
  }

  private static List<String> evidence(List<WinEvent> failures, WinEvent logon) {
    var ids = new ArrayList<>(failures.stream().map(WinEvent::eventId).toList());
    ids.add(logon.eventId());
    return ids;
  }
}
