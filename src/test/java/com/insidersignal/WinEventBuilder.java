package com.insidersignal;

import com.insidersignal.event.EventCode;
import com.insidersignal.event.WinEvent;
import java.time.Instant;
import java.util.UUID;

/** Test data builder: {@code aLogon().by("alice@contoso.example").at(time).build()}. */
public final class WinEventBuilder {

  private String eventId = UUID.randomUUID().toString();
  private final EventCode code;
  private Instant occurredAt = Instant.parse("2026-06-15T10:00:00Z");
  private String upn = "alice@contoso.example";
  private String host = "wks-01";

  private WinEventBuilder(EventCode code) {
    this.code = code;
  }

  public static WinEventBuilder aLogon() {
    return new WinEventBuilder(EventCode.LOGON_SUCCESS);
  }

  public static WinEventBuilder aFailedLogon() {
    return new WinEventBuilder(EventCode.LOGON_FAILURE);
  }

  public WinEventBuilder id(String eventId) {
    this.eventId = eventId;
    return this;
  }

  public WinEventBuilder by(String upn) {
    this.upn = upn;
    return this;
  }

  public WinEventBuilder at(Instant occurredAt) {
    this.occurredAt = occurredAt;
    return this;
  }

  public WinEventBuilder on(String host) {
    this.host = host;
    return this;
  }

  public WinEvent build() {
    return new WinEvent(eventId, code, occurredAt, upn, host, null);
  }
}
