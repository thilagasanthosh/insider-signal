package com.insidersignal.detect;

import java.time.Instant;

/** Per-user behaviour baselines. */
public interface BaselineStore {

  /**
   * The user's baseline over the 30 UTC days before the day of {@code at}. The current day is left
   * out so an event cannot count towards its own baseline.
   */
  UserBaseline priorTo(String upn, Instant at);
}
