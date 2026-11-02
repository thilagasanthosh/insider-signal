package com.insidersignal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.insidersignal.detect.BaselineStore;
import com.insidersignal.detect.OffHoursConfig;
import com.insidersignal.detect.RuleCatalog;
import com.insidersignal.detect.RuleId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/** The spec's two use cases and the rule-change demo, through the REST API and real backends. */
class InsiderSignalEndToEndIT extends IntegrationTest {

  private static final Duration DAY = Duration.ofDays(1);

  @Autowired private TestRestTemplate rest;
  @Autowired private Clock clock;
  @Autowired private RuleCatalog catalog;
  @Autowired private BaselineStore baselines;

  @Value("${insider-signal.kafka.events-topic}")
  private String topic;

  private final String upn = UUID.randomUUID() + "@contoso.example";

  private Map<String, Object> event(int code, Instant at, String host) {
    return Map.of(
        "eventId", UUID.randomUUID().toString(),
        "eventCode", code,
        "occurredAt", at.toString(),
        "upn", upn,
        "host", host);
  }

  private void ingest(List<Map<String, Object>> events) {
    var response = rest.postForEntity("/api/v1/events", Map.of("events", events), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody().get("accepted").asInt()).isEqualTo(events.size());
  }

  private JsonNode alerts() {
    return rest.getForObject("/api/v1/alerts?upn={upn}", JsonNode.class, upn);
  }

  private JsonNode risk() {
    return rest.getForObject("/api/v1/users/{upn}/risk", JsonNode.class, upn);
  }

  private void awaitAlertCount(int count) {
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(alerts()).hasSize(count));
  }

  /**
   * History spread over hours other than {@code quietHour}, from two days back so that every event
   * is past the maximum age and only builds the baseline.
   */
  private List<Map<String, Object>> history(Instant now, int quietHour, int quietHourLogons) {
    var today = now.truncatedTo(ChronoUnit.DAYS);
    var events = new ArrayList<Map<String, Object>>();
    for (int day = 1; day <= 25; day++) {
      int usualHour = (quietHour + 6 + day % 8) % 24;
      events.add(
          event(
              4624,
              today.minus(DAY.multipliedBy(day + 1)).plusSeconds(usualHour * 3600L),
              "wks-01"));
    }
    for (int i = 1; i <= quietHourLogons; i++) {
      events.add(
          event(
              4624, today.minus(DAY.multipliedBy(i + 1)).plusSeconds(quietHour * 3600L), "wks-01"));
    }
    return events;
  }

  private static int hourOf(Instant at) {
    return at.atZone(ZoneOffset.UTC).getHour();
  }

  private void ingestHistoryAndWaitForBaseline(List<Map<String, Object>> history, Instant now) {
    ingest(history);
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> baselines.priorTo(upn, now).totalLogons() >= history.size());
  }

  @Test
  @Tag("F-02")
  void bruteForceFollowedBySuccessRaisesAHighAlertWithEvidence() {
    var now = clock.instant();
    var events = new ArrayList<Map<String, Object>>();
    for (int i = 6; i >= 1; i--) {
      events.add(event(4625, now.minusSeconds(i * 10L), "wks-01"));
    }
    var logon = event(4624, now, "wks-01");
    events.add(logon);

    ingest(events);

    awaitAlertCount(1);
    var alert = alerts().get(0);
    assertThat(alert.get("ruleId").asText()).isEqualTo("brute-force");
    assertThat(alert.get("severity").asText()).isEqualTo("HIGH");
    assertThat(alert.get("evidenceEventIds")).hasSize(7);
    assertThat(alert.get("id").asText()).isEqualTo("brute-force:" + logon.get("eventId"));
    assertThat(risk().get("score").asInt()).isEqualTo(60);
    assertThat(risk().get("reasons").get(0).get("reason").asText()).contains("6 failed logons");
  }

  @Test
  @Tag("F-02")
  void failuresArrivingOutOfOrderInTheSameBatchStillCount() {
    var now = clock.instant();
    var events = new ArrayList<Map<String, Object>>();
    events.add(event(4624, now, "wks-01"));
    for (int i = 5; i >= 1; i--) {
      events.add(event(4625, now.minusSeconds(i * 10L), "wks-01"));
    }

    ingest(events);

    awaitAlertCount(1);
  }

  @Test
  @Tag("F-01")
  void redeliveredEventsDoNotDuplicateAlerts() {
    var now = clock.instant();
    var events = new ArrayList<Map<String, Object>>();
    for (int i = 5; i >= 1; i--) {
      events.add(event(4625, now.minusSeconds(i * 10L), "wks-01"));
    }
    events.add(event(4624, now, "wks-01"));

    ingest(events);
    awaitAlertCount(1);
    ingest(events);
    ingest(List.of(event(4625, now, "wks-01")));

    await()
        .pollDelay(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(alerts()).hasSize(1));
  }

  @Test
  @Tag("F-03")
  void logonFromANewHostRaisesRiskWithAReason() {
    var now = clock.instant();
    var history = history(now, hourOf(now), 3);
    ingestHistoryAndWaitForBaseline(history, now);

    ingest(List.of(event(4624, now, "srv-finance")));

    awaitAlertCount(1);
    assertThat(alerts().get(0).get("ruleId").asText()).isEqualTo("new-host");
    assertThat(risk().get("score").asInt()).isEqualTo(30);
    assertThat(risk().get("reasons").get(0).get("reason").asText()).contains("srv-finance");
  }

  @Test
  @Tag("F-03")
  void historicalEventsBuildTheBaselineWithoutRaisingAlerts() {
    var now = clock.instant();
    ingestHistoryAndWaitForBaseline(history(now, hourOf(now), 0), now);

    assertThat(alerts()).isEmpty();
  }

  /** The demo: tighten off-hours through the API; the next event uses it, without a restart. */
  @Test
  @Tag("F-05")
  void changingTheOffHoursRuleAppliesToTheNextEvent() {
    var now = clock.instant();
    var original = catalog.config(RuleId.OFF_HOURS, OffHoursConfig.class);
    ingestHistoryAndWaitForBaseline(history(now, hourOf(now), 3), now);

    ingest(List.of(event(4624, now, "wks-01")));
    // A later event for the user being fully processed shows the first logon raised nothing.
    var firstProbeProcessed = event(4624, now.plusMillis(1), "srv-finance");
    ingest(List.of(firstProbeProcessed));
    awaitAlertCount(1);
    assertThat(alerts().get(0).get("ruleId").asText()).isEqualTo("new-host");

    var update =
        Map.of("config", Map.of("minBaselineLogons", 20, "minLogonsInHour", 5, "score", 30));
    var response =
        rest.exchange(
            "/api/v1/rules/off-hours", HttpMethod.PUT, new HttpEntity<>(update), JsonNode.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    try {
      ingest(List.of(event(4624, now.plusMillis(2), "wks-01")));
      awaitAlertCount(2);
      assertThat(alerts().get(0).get("ruleId").asText()).isEqualTo("off-hours");
    } finally {
      catalog.update(RuleId.OFF_HOURS, original);
    }
  }

  @Test
  @Tag("F-01")
  void aMalformedKafkaRecordIsSkippedAndLaterEventsStillProcess() {
    sendRawToEventsTopic("{not json");
    var now = clock.instant();
    var events = new ArrayList<Map<String, Object>>();
    for (int i = 5; i >= 1; i--) {
      events.add(event(4625, now.minusSeconds(i * 10L), "wks-01"));
    }
    events.add(event(4624, now, "wks-01"));

    ingest(events);

    awaitAlertCount(1);
  }

  private void sendRawToEventsTopic(String value) {
    Map<String, Object> config =
        Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
    try (var producer =
        new KafkaProducer<>(config, new StringSerializer(), new StringSerializer())) {
      producer.send(new ProducerRecord<>(topic, upn, value));
    }
  }
}
