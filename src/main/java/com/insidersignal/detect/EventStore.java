package com.insidersignal.detect;

import com.insidersignal.event.WinEvent;
import java.time.Instant;
import java.util.List;

/** Raw event history. */
public interface EventStore {

  /** Safe to call again with the same events: duplicates collapse by event id. */
  void saveAll(List<WinEvent> events);

  /** A user's logon successes and failures in [from, to], oldest first. */
  List<WinEvent> logonAttempts(String upn, Instant from, Instant to);
}
