package com.insidersignal.detect;

import static com.insidersignal.WinEventBuilder.aLogon;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OffHoursRuleTest {

  private static final Instant AT_2AM = Instant.parse("2026-06-15T02:30:00Z");

  private final FakeBaselineStore baseline = new FakeBaselineStore();
  private final FakeRuleStore ruleStore = new FakeRuleStore();
  private final OffHoursRule rule =
      new OffHoursRule(
          baseline, new RuleCatalog(ruleStore, Clock.systemUTC(), Duration.ofMinutes(10)));

  @Test
  void raisesMediumAlertForAnHourTheUserNeverWorks() {
    baseline.with(9, "wks-01", 40);

    var alert = rule.evaluate(aLogon().id("e1").at(AT_2AM).build()).orElseThrow();

    assertThat(alert.id()).isEqualTo("off-hours:e1");
    assertThat(alert.severity()).isEqualTo(Severity.MEDIUM);
    assertThat(alert.score()).isEqualTo(30);
    assertThat(alert.reason()).contains("02:00 UTC");
    assertThat(alert.evidenceEventIds()).containsExactly("e1");
  }

  @Test
  void ignoresAnHourTheUserUsuallyWorks() {
    baseline.with(2, "wks-01", 40);

    assertThat(rule.evaluate(aLogon().at(AT_2AM).build())).isEmpty();
  }

  @Test
  void doesNotJudgeAUserWithTooLittleHistory() {
    baseline.with(9, "wks-01", 19);

    assertThat(rule.evaluate(aLogon().at(AT_2AM).build())).isEmpty();
  }

  @Test
  void addsLogonsAcrossHostsWhenJudgingAnHour() {
    baseline.with(9, "wks-01", 40).with(2, "wks-01", 2).with(2, "wks-02", 3);
    ruleStore.set(RuleId.OFF_HOURS, new OffHoursConfig(20, 5, 30));

    assertThat(rule.evaluate(aLogon().at(AT_2AM).build())).isEmpty();
  }

  @Test
  void raisingTheThresholdMakesARareHourCountAsOffHours() {
    baseline.with(9, "wks-01", 40).with(2, "wks-01", 3);
    assertThat(rule.evaluate(aLogon().at(AT_2AM).build())).isEmpty();

    var stricter =
        new OffHoursRule(
            baseline,
            new RuleCatalog(storeWithThreshold(5), Clock.systemUTC(), Duration.ofMinutes(10)));

    assertThat(stricter.evaluate(aLogon().at(AT_2AM).build())).isPresent();
  }

  private static FakeRuleStore storeWithThreshold(int minLogonsInHour) {
    var store = new FakeRuleStore();
    store.set(RuleId.OFF_HOURS, new OffHoursConfig(20, minLogonsInHour, 30));
    return store;
  }
}
