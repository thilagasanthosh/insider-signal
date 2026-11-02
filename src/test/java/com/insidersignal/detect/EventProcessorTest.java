package com.insidersignal.detect;

import static com.insidersignal.WinEventBuilder.aFailedLogon;
import static com.insidersignal.WinEventBuilder.aLogon;
import static org.assertj.core.api.Assertions.assertThat;

import com.insidersignal.event.WinEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EventProcessorTest {

  private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");

  private final FakeEventStore events = new FakeEventStore();
  private final FakeAlertStore alerts = new FakeAlertStore();
  private final List<String> evaluationOrder = new ArrayList<>();
  private final List<Integer> savedCountWhenEvaluated = new ArrayList<>();

  private final LogonRule alwaysFires =
      logon -> {
        evaluationOrder.add(logon.eventId());
        savedCountWhenEvaluated.add(events.saved().size());
        return Optional.of(
            new Alert(
                "rule:" + logon.eventId(),
                RuleId.NEW_HOST,
                logon.upn(),
                Severity.MEDIUM,
                30,
                logon.occurredAt(),
                "why",
                List.of(logon.eventId())));
      };

  private final EventProcessor processor =
      new EventProcessor(
          events,
          alerts,
          List.of(alwaysFires),
          Clock.fixed(NOW, ZoneOffset.UTC),
          Duration.ofHours(24));

  private WinEvent logon(String id, Instant at) {
    return aLogon().id(id).at(at).build();
  }

  @Test
  void savesTheWholeBatchBeforeEvaluatingSoRulesSeeItsEvents() {
    processor.process(
        List.of(logon("l1", NOW.minusSeconds(5)), aFailedLogon().at(NOW.minusSeconds(10)).build()));

    assertThat(savedCountWhenEvaluated).containsExactly(2);
  }

  @Test
  void evaluatesOnlySuccessfulLogons() {
    processor.process(List.of(aFailedLogon().id("f1").at(NOW.minusSeconds(10)).build()));

    assertThat(evaluationOrder).isEmpty();
    assertThat(alerts.saved()).isEmpty();
  }

  @Test
  void savesAlertsFromRules() {
    processor.process(List.of(logon("l1", NOW.minusSeconds(5))));

    assertThat(alerts.saved()).extracting(Alert::id).containsExactly("rule:l1");
  }

  @Test
  void evaluatesOutOfOrderLogonsInEventTimeOrder() {
    processor.process(
        List.of(logon("later", NOW.minusSeconds(5)), logon("earlier", NOW.minusSeconds(50))));

    assertThat(evaluationOrder).containsExactly("earlier", "later");
  }

  @Test
  void storesButDoesNotEvaluateEventsOlderThanTheMaximumAge() {
    var old = logon("old", NOW.minus(Duration.ofHours(24)).minusSeconds(1));

    processor.process(List.of(old));

    assertThat(events.saved()).containsExactly(old);
    assertThat(evaluationOrder).isEmpty();
  }

  @Test
  void reprocessingABatchDoesNotDuplicateAlerts() {
    var batch = List.of(logon("l1", NOW.minusSeconds(5)));

    processor.process(batch);
    processor.process(batch);

    assertThat(alerts.saved()).hasSize(1);
  }
}
