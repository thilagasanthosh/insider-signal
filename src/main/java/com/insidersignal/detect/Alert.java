package com.insidersignal.detect;

import com.insidersignal.event.WinEvent;
import java.time.Instant;
import java.util.List;

/**
 * A rule firing for a user.
 *
 * @param id rule plus triggering event, so reprocessing an event cannot duplicate its alert
 */
public record Alert(
    String id,
    RuleId ruleId,
    String upn,
    Severity severity,
    int score,
    Instant occurredAt,
    String reason,
    List<String> evidenceEventIds) {

  static Alert forLogon(
      RuleId ruleId,
      WinEvent logon,
      Severity severity,
      int score,
      String reason,
      List<String> evidenceEventIds) {
    return new Alert(
        ruleId.publicName() + ":" + logon.eventId(),
        ruleId,
        logon.upn(),
        severity,
        score,
        logon.occurredAt(),
        reason,
        evidenceEventIds);
  }
}
