package com.insidersignal.detect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RiskServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
  private static final String UPN = "alice@contoso.example";

  private final FakeAlertStore alerts = new FakeAlertStore();
  private final RiskService service =
      new RiskService(alerts, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(7));

  private void givenAlert(String id, int score, Instant occurredAt) {
    alerts.save(
        new Alert(
            id, RuleId.OFF_HOURS, UPN, Severity.MEDIUM, score, occurredAt, "why " + id, List.of()));
  }

  @Test
  void scoresAUserWithNoAlertsAsZero() {
    var risk = service.riskFor("nobody@contoso.example");

    assertThat(risk.score()).isZero();
    assertThat(risk.alerts()).isEmpty();
  }

  @Test
  void addsRecentAlertScoresAndKeepsThemAsReasons() {
    givenAlert("a", 30, NOW.minusSeconds(60));
    givenAlert("b", 30, NOW.minusSeconds(30));

    var risk = service.riskFor(UPN);

    assertThat(risk.score()).isEqualTo(60);
    assertThat(risk.alerts()).extracting(Alert::reason).containsExactly("why a", "why b");
  }

  @Test
  void capsTheScoreAt100() {
    givenAlert("a", 60, NOW.minusSeconds(60));
    givenAlert("b", 60, NOW.minusSeconds(30));

    assertThat(service.riskFor(UPN).score()).isEqualTo(100);
  }

  @Test
  void ignoresAlertsOlderThanTheRiskWindow() {
    givenAlert("old", 30, NOW.minus(Duration.ofDays(7)).minusSeconds(1));

    assertThat(service.riskFor(UPN).score()).isZero();
  }

  @Test
  void treatsUpnCaseInsensitively() {
    givenAlert("a", 30, NOW.minusSeconds(60));

    assertThat(service.riskFor("Alice@Contoso.Example").score()).isEqualTo(30);
  }
}
