package com.insidersignal.detect;

import com.insidersignal.event.WinEvent;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** A logon at an hour of day the user rarely or never works. */
public class OffHoursRule implements LogonRule {

  private final BaselineStore baselines;
  private final RuleCatalog rules;

  public OffHoursRule(BaselineStore baselines, RuleCatalog rules) {
    this.baselines = baselines;
    this.rules = rules;
  }

  @Override
  public Optional<Alert> evaluate(WinEvent logon) {
    var config = rules.config(RuleId.OFF_HOURS, OffHoursConfig.class);
    var baseline = baselines.priorTo(logon.upn(), logon.occurredAt());
    if (baseline.totalLogons() < config.minBaselineLogons()) {
      return Optional.empty();
    }
    int hour = logon.occurredAt().atZone(ZoneOffset.UTC).getHour();
    long logonsInHour = baseline.logonsInHour(hour);
    if (logonsInHour >= config.minLogonsInHour()) {
      return Optional.empty();
    }
    var reason =
        "Logon at %02d:00 UTC: %d logons in this hour over the last 30 days, usual needs %d"
            .formatted(hour, logonsInHour, config.minLogonsInHour());
    return Optional.of(
        Alert.forLogon(
            RuleId.OFF_HOURS,
            logon,
            Severity.MEDIUM,
            config.score(),
            reason,
            List.of(logon.eventId())));
  }
}
