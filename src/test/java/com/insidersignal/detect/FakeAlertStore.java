package com.insidersignal.detect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FakeAlertStore implements AlertStore {

  private final List<Alert> alerts = new ArrayList<>();

  public List<Alert> saved() {
    return alerts;
  }

  @Override
  public void save(Alert alert) {
    if (alerts.stream().noneMatch(a -> a.id().equals(alert.id()))) {
      alerts.add(alert);
    }
  }

  @Override
  public List<Alert> latest(String upn, int limit) {
    return alerts.stream().limit(limit).toList();
  }

  @Override
  public List<Alert> forUserSince(String upn, Instant since) {
    return alerts.stream()
        .filter(a -> a.upn().equals(upn) && !a.occurredAt().isBefore(since))
        .toList();
  }
}
