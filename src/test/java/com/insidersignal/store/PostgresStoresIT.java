package com.insidersignal.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.insidersignal.IntegrationTest;
import com.insidersignal.detect.Alert;
import com.insidersignal.detect.AlertStore;
import com.insidersignal.detect.BruteForceConfig;
import com.insidersignal.detect.NewHostConfig;
import com.insidersignal.detect.OffHoursConfig;
import com.insidersignal.detect.RuleCatalog;
import com.insidersignal.detect.RuleId;
import com.insidersignal.detect.RuleStore;
import com.insidersignal.detect.Severity;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class PostgresStoresIT extends IntegrationTest {

  private static final Instant T0 = Instant.parse("2026-06-15T12:00:00.123Z");

  @Autowired private AlertStore alerts;
  @Autowired private RuleStore rules;
  @Autowired private RuleCatalog catalog;
  @Autowired private JdbcClient jdbc;

  private final String upn = UUID.randomUUID() + "@contoso.example";

  private Alert alert(String id, Instant occurredAt) {
    return new Alert(
        id + upn,
        RuleId.BRUTE_FORCE,
        upn,
        Severity.HIGH,
        60,
        occurredAt,
        "5 failed logons",
        List.of("f1", "f2", "logon"));
  }

  @Test
  void roundTripsAnAlertWithItsJsonbDetail() {
    var alert = alert("a", T0);

    alerts.save(alert);

    assertThat(alerts.latest(upn, 10)).containsExactly(alert);
  }

  @Test
  void storesEvidenceAsQueryableJsonb() {
    alerts.save(alert("a", T0));

    var firstEvidence =
        jdbc.sql("SELECT detail->'evidenceEventIds'->>0 FROM alert WHERE upn = ?")
            .param(upn)
            .query(String.class)
            .single();

    assertThat(firstEvidence).isEqualTo("f1");
  }

  @Test
  void savingTheSameAlertTwiceKeepsOneRow() {
    alerts.save(alert("a", T0));
    alerts.save(alert("a", T0));

    assertThat(alerts.latest(upn, 10)).hasSize(1);
  }

  @Test
  void latestReturnsNewestFirstUpToTheLimit() {
    alerts.save(alert("old", T0));
    alerts.save(alert("new", T0.plusSeconds(60)));

    assertThat(alerts.latest(upn, 1)).extracting(Alert::id).containsExactly("new" + upn);
  }

  @Test
  void latestWithoutAUserReturnsAllUsers() {
    alerts.save(alert("a", T0));

    assertThat(alerts.latest(null, 500)).extracting(Alert::upn).contains(upn);
  }

  @Test
  void forUserSinceReturnsAlertsAtOrAfterTheCutoffOldestFirst() {
    alerts.save(alert("too-old", T0.minusMillis(1)));
    alerts.save(alert("later", T0.plusSeconds(1)));
    alerts.save(alert("edge", T0));

    assertThat(alerts.forUserSince(upn, T0))
        .extracting(Alert::id)
        .containsExactly("edge" + upn, "later" + upn);
  }

  @Test
  void flywaySeedsDefaultConfigForEveryRule() {
    assertThat(rules.find(RuleId.BRUTE_FORCE).config()).isEqualTo(new BruteForceConfig(5, 5, 60));
    assertThat(rules.find(RuleId.NEW_HOST).config()).isEqualTo(new NewHostConfig(20, 30));
    assertThat(rules.findAll()).hasSize(RuleId.values().length);
  }

  @Test
  void updatesARuleAndItsTimestamp() {
    var original = rules.find(RuleId.OFF_HOURS);
    var changed = new OffHoursConfig(25, 3, 35);

    var updated = rules.update(RuleId.OFF_HOURS, changed, T0);

    assertThat(updated.config()).isEqualTo(changed);
    assertThat(updated.updatedAt()).isEqualTo(T0);
    assertThat(rules.find(RuleId.OFF_HOURS)).isEqualTo(updated);
    rules.update(RuleId.OFF_HOURS, original.config(), original.updatedAt());
  }

  /** The SQL update stands in for another instance: only the NOTIFY can reach this cache. */
  @Test
  @Tag("F-05")
  void aRuleChangedElsewhereReachesThisInstancesCacheWithoutARestart() {
    var original = rules.find(RuleId.NEW_HOST);
    assertThat(catalog.config(RuleId.NEW_HOST, NewHostConfig.class).score()).isEqualTo(30);

    jdbc.sql(
            """
            UPDATE detection_rule
            SET config = '{"minBaselineLogons": 20, "score": 77}'::jsonb
            WHERE id = 'new-host'
            """)
        .update();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(catalog.config(RuleId.NEW_HOST, NewHostConfig.class).score())
                    .isEqualTo(77));
    catalog.update(RuleId.NEW_HOST, original.config());
  }
}
