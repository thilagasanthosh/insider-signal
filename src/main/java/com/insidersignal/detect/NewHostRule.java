package com.insidersignal.detect;

import com.insidersignal.event.WinEvent;
import java.util.List;
import java.util.Optional;

/** A logon from a host the user has not used recently. */
public class NewHostRule implements LogonRule {

  private final BaselineStore baselines;
  private final RuleCatalog rules;

  public NewHostRule(BaselineStore baselines, RuleCatalog rules) {
    this.baselines = baselines;
    this.rules = rules;
  }

  @Override
  public Optional<Alert> evaluate(WinEvent logon) {
    var config = rules.config(RuleId.NEW_HOST, NewHostConfig.class);
    var baseline = baselines.priorTo(logon.upn(), logon.occurredAt());
    if (baseline.totalLogons() < config.minBaselineLogons() || baseline.hasUsedHost(logon.host())) {
      return Optional.empty();
    }
    var reason = "Logon from %s: host not used in the last 30 days".formatted(logon.host());
    return Optional.of(
        Alert.forLogon(
            RuleId.NEW_HOST,
            logon,
            Severity.MEDIUM,
            config.score(),
            reason,
            List.of(logon.eventId())));
  }
}
