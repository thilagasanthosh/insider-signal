package com.insidersignal.detect;

import static com.insidersignal.WinEventBuilder.aFailedLogon;
import static com.insidersignal.WinEventBuilder.aLogon;
import static org.assertj.core.api.Assertions.assertThat;

import com.insidersignal.event.WinEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BruteForceRuleTest {

  private static final Instant LOGON_TIME = Instant.parse("2026-06-15T10:00:00Z");

  private final FakeEventStore events = new FakeEventStore();
  private final FakeRuleStore ruleStore = new FakeRuleStore();
  private final BruteForceRule rule =
      new BruteForceRule(
          events, new RuleCatalog(ruleStore, Clock.systemUTC(), Duration.ofMinutes(10)));

  private WinEvent failureSecondsBefore(int seconds) {
    return aFailedLogon().id("f" + seconds).at(LOGON_TIME.minusSeconds(seconds)).build();
  }

  private void givenFailuresAtSecondsBeforeLogon(int... seconds) {
    for (int s : seconds) {
      events.add(failureSecondsBefore(s));
    }
  }

  private WinEvent successfulLogon() {
    var logon = aLogon().id("logon").at(LOGON_TIME).build();
    events.add(logon);
    return logon;
  }

  @Test
  void raisesHighAlertForFiveFailuresThenSuccessWithinWindow() {
    givenFailuresAtSecondsBeforeLogon(250, 200, 150, 100, 50);

    var alert = rule.evaluate(successfulLogon()).orElseThrow();

    assertThat(alert.id()).isEqualTo("brute-force:logon");
    assertThat(alert.ruleId()).isEqualTo(RuleId.BRUTE_FORCE);
    assertThat(alert.upn()).isEqualTo("alice@contoso.example");
    assertThat(alert.severity()).isEqualTo(Severity.HIGH);
    assertThat(alert.score()).isEqualTo(60);
    assertThat(alert.occurredAt()).isEqualTo(LOGON_TIME);
    assertThat(alert.reason()).contains("5 failed logons");
  }

  @Test
  void citesEveryFailureAndTheLogonAsEvidence() {
    givenFailuresAtSecondsBeforeLogon(250, 200, 150, 100, 50);

    var alert = rule.evaluate(successfulLogon()).orElseThrow();

    assertThat(alert.evidenceEventIds())
        .containsExactly("f250", "f200", "f150", "f100", "f50", "logon");
  }

  @Test
  void ignoresFourFailures() {
    givenFailuresAtSecondsBeforeLogon(200, 150, 100, 50);

    assertThat(rule.evaluate(successfulLogon())).isEmpty();
  }

  @Test
  void countsAFailureExactlyAtTheWindowEdge() {
    givenFailuresAtSecondsBeforeLogon(300, 200, 150, 100, 50);

    assertThat(rule.evaluate(successfulLogon())).isPresent();
  }

  @Test
  void ignoresAFailureJustOutsideTheWindow() {
    givenFailuresAtSecondsBeforeLogon(301, 200, 150, 100, 50);

    assertThat(rule.evaluate(successfulLogon())).isEmpty();
  }

  @Test
  void ignoresFailuresForOtherAccounts() {
    givenFailuresAtSecondsBeforeLogon(250, 200, 150, 100);
    events.add(aFailedLogon().by("bob@contoso.example").at(LOGON_TIME.minusSeconds(50)).build());

    assertThat(rule.evaluate(successfulLogon())).isEmpty();
  }

  @Test
  void anEarlierSuccessResetsTheCountSoASecondLogonDoesNotAlertAgain() {
    givenFailuresAtSecondsBeforeLogon(250, 200, 150, 100, 50);
    events.add(aLogon().id("first-logon").at(LOGON_TIME.minusSeconds(40)).build());

    assertThat(rule.evaluate(successfulLogon())).isEmpty();
  }

  @Test
  void usesTheCurrentlyConfiguredThresholdAndWindow() {
    ruleStore.set(RuleId.BRUTE_FORCE, new BruteForceConfig(2, 1, 40));
    givenFailuresAtSecondsBeforeLogon(90, 50, 40);

    var alert = rule.evaluate(successfulLogon()).orElseThrow();

    assertThat(alert.score()).isEqualTo(40);
    assertThat(alert.evidenceEventIds()).containsExactly("f50", "f40", "logon");
  }
}
