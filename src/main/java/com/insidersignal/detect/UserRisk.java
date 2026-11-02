package com.insidersignal.detect;

import java.util.List;

/**
 * @param score sum of the alerts' scores, capped at {@value #MAX_SCORE}
 * @param alerts the reasons behind the score
 */
public record UserRisk(String upn, int score, List<Alert> alerts) {

  private static final int MAX_SCORE = 100;

  static UserRisk from(String upn, List<Alert> alerts) {
    int total = alerts.stream().mapToInt(Alert::score).sum();
    return new UserRisk(upn, Math.min(total, MAX_SCORE), alerts);
  }
}
