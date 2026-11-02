package com.insidersignal.detect;

import static com.insidersignal.WinEventBuilder.aLogon;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class NewHostRuleTest {

  private final FakeBaselineStore baseline = new FakeBaselineStore();
  private final NewHostRule rule =
      new NewHostRule(
          baseline,
          new RuleCatalog(new FakeRuleStore(), Clock.systemUTC(), Duration.ofMinutes(10)));

  @Test
  void raisesMediumAlertForAHostTheUserHasNeverUsed() {
    baseline.with(9, "wks-01", 40);

    var alert = rule.evaluate(aLogon().id("e1").on("SRV-FINANCE").build()).orElseThrow();

    assertThat(alert.id()).isEqualTo("new-host:e1");
    assertThat(alert.severity()).isEqualTo(Severity.MEDIUM);
    assertThat(alert.score()).isEqualTo(30);
    assertThat(alert.reason()).contains("srv-finance");
    assertThat(alert.evidenceEventIds()).containsExactly("e1");
  }

  @Test
  void ignoresAHostTheUserHasUsed() {
    baseline.with(9, "wks-01", 40);

    assertThat(rule.evaluate(aLogon().on("WKS-01").build())).isEmpty();
  }

  @Test
  void doesNotJudgeAUserWithTooLittleHistory() {
    baseline.with(9, "wks-01", 19);

    assertThat(rule.evaluate(aLogon().on("srv-finance").build())).isEmpty();
  }
}
