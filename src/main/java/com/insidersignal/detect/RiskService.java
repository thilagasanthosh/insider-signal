package com.insidersignal.detect;

import com.insidersignal.event.WinEvent;
import java.time.Clock;
import java.time.Duration;

/** Computes a user's risk score on read, from their recent alerts. */
public class RiskService {

  private final AlertStore alerts;
  private final Clock clock;
  private final Duration window;

  public RiskService(AlertStore alerts, Clock clock, Duration window) {
    this.alerts = alerts;
    this.clock = clock;
    this.window = window;
  }

  public UserRisk riskFor(String upn) {
    var normalisedUpn = WinEvent.normalise(upn);
    var since = clock.instant().minus(window);
    return UserRisk.from(normalisedUpn, alerts.forUserSince(normalisedUpn, since));
  }
}
