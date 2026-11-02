package com.insidersignal.detect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class RuleCatalogTest {

  private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");

  private final FakeRuleStore store = new FakeRuleStore();
  private final RuleCatalog catalog =
      new RuleCatalog(store, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(10));

  @Test
  void servesRepeatReadsFromTheCache() {
    catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class);
    catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class);

    assertThat(store.loads()).isEqualTo(1);
  }

  @Test
  void keepsServingTheCachedConfigUntilInvalidated() {
    catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class);
    store.set(RuleId.OFF_HOURS, new OffHoursConfig(20, 5, 30));

    assertThat(catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class).minLogonsInHour())
        .isEqualTo(1);
  }

  @Test
  void reloadsOneRuleAfterItIsInvalidated() {
    catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class);
    store.set(RuleId.OFF_HOURS, new OffHoursConfig(20, 5, 30));

    catalog.invalidate(RuleId.OFF_HOURS);

    assertThat(catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class).minLogonsInHour())
        .isEqualTo(5);
  }

  @Test
  void reloadsEveryRuleAfterInvalidateAll() {
    catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class);
    store.set(RuleId.OFF_HOURS, new OffHoursConfig(20, 5, 30));

    catalog.invalidateAll();

    assertThat(catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class).minLogonsInHour())
        .isEqualTo(5);
  }

  @Test
  void updateAppliesImmediatelyOnThisInstance() {
    catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class);

    var updated = catalog.update(RuleId.OFF_HOURS, new OffHoursConfig(20, 5, 30));

    assertThat(updated.updatedAt()).isEqualTo(NOW);
    assertThat(catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class).minLogonsInHour())
        .isEqualTo(5);
  }

  @Test
  void looksUpRuleIdsByTheirPublicName() {
    assertThat(RuleId.fromName("off-hours")).contains(RuleId.OFF_HOURS);
    assertThat(RuleId.fromName("nope")).isEmpty();
  }
}
