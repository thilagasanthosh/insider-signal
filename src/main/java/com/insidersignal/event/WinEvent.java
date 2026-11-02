package com.insidersignal.event;

import java.time.Instant;
import java.util.Locale;

/**
 * A Windows Security event.
 *
 * @param eventId unique per event; the idempotency key for redelivery
 * @param targetGroup group the user was added to; null except for group-membership events
 */
public record WinEvent(
    String eventId,
    EventCode code,
    Instant occurredAt,
    String upn,
    String host,
    String targetGroup) {

  public WinEvent {
    upn = normalise(upn);
    host = normalise(host);
  }

  /** Active Directory names are case-insensitive, so one spelling is used everywhere. */
  public static String normalise(String name) {
    return name.toLowerCase(Locale.ROOT);
  }

  public boolean isSuccessfulLogon() {
    return code == EventCode.LOGON_SUCCESS;
  }

  public boolean isFailedLogon() {
    return code == EventCode.LOGON_FAILURE;
  }
}
