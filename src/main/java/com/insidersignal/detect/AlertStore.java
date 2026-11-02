package com.insidersignal.detect;

import java.time.Instant;
import java.util.List;

/** Durable alert history. */
public interface AlertStore {

  /** Saving an alert whose id already exists does nothing. */
  void save(Alert alert);

  /**
   * @param upn only this user's alerts; null for all users
   * @return newest first
   */
  List<Alert> latest(String upn, int limit);

  /** A user's alerts that occurred at or after {@code since}, oldest first. */
  List<Alert> forUserSince(String upn, Instant since);
}
