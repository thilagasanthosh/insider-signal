package com.insidersignal.store;

import static com.insidersignal.WinEventBuilder.aFailedLogon;
import static com.insidersignal.WinEventBuilder.aLogon;
import static org.assertj.core.api.Assertions.assertThat;

import com.insidersignal.IntegrationTest;
import com.insidersignal.detect.BaselineStore;
import com.insidersignal.detect.EventStore;
import com.insidersignal.detect.UserBaseline;
import com.insidersignal.event.EventCode;
import com.insidersignal.event.WinEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ClickHouseStoresIT extends IntegrationTest {

  // In British Summer Time, so a JVM time-zone conversion in the driver would shift it.
  private static final Instant SUMMER_NOON = Instant.parse("2026-06-15T12:34:56.789Z");

  @Autowired private EventStore events;
  @Autowired private BaselineStore baselines;

  private final String upn = UUID.randomUUID() + "@contoso.example";

  private List<WinEvent> attemptsAround(Instant time) {
    return events.logonAttempts(upn, time.minusSeconds(60), time.plusSeconds(60));
  }

  @Test
  void roundTripsAnEventExactlyAcrossTimeZones() {
    var event =
        new WinEvent(
            "e-" + UUID.randomUUID(), EventCode.LOGON_FAILURE, SUMMER_NOON, upn, "wks-01", null);

    events.saveAll(List.of(event));

    assertThat(attemptsAround(SUMMER_NOON)).containsExactly(event);
  }

  @Test
  void returnsAttemptsOldestFirstWithinInclusiveBounds() {
    var early = aFailedLogon().by(upn).at(SUMMER_NOON.minusSeconds(60)).build();
    var late = aLogon().by(upn).at(SUMMER_NOON.plusSeconds(60)).build();
    var tooEarly = aFailedLogon().by(upn).at(SUMMER_NOON.minusSeconds(61)).build();

    events.saveAll(List.of(late, tooEarly, early));

    assertThat(attemptsAround(SUMMER_NOON)).containsExactly(early, late);
  }

  @Test
  void leavesGroupChangesOutOfLogonAttempts() {
    var groupChange =
        new WinEvent(
            "g-" + UUID.randomUUID(),
            EventCode.ADDED_TO_LOCAL_GROUP,
            SUMMER_NOON,
            upn,
            "dc-01",
            "Administrators");

    events.saveAll(List.of(groupChange));

    assertThat(attemptsAround(SUMMER_NOON)).isEmpty();
  }

  @Test
  void savingTheSameEventTwiceKeepsOneCopy() {
    var event = aFailedLogon().by(upn).at(SUMMER_NOON).build();

    events.saveAll(List.of(event));
    events.saveAll(List.of(event));

    assertThat(attemptsAround(SUMMER_NOON)).hasSize(1);
  }

  @Test
  void baselineCountsLogonsPerHourAndHostOverPriorDays() {
    var day = Duration.ofDays(1);
    events.saveAll(
        List.of(
            aLogon().by(upn).on("wks-01").at(SUMMER_NOON.minus(day)).build(),
            aLogon().by(upn).on("wks-01").at(SUMMER_NOON.minus(day.multipliedBy(2))).build(),
            aLogon().by(upn).on("srv-02").at(SUMMER_NOON.minus(day)).build()));

    assertThat(baselines.priorTo(upn, SUMMER_NOON).entries())
        .containsExactlyInAnyOrder(
            new UserBaseline.Entry(12, "wks-01", 2), new UserBaseline.Entry(12, "srv-02", 1));
  }

  @Test
  void baselineLeavesOutFailedLogons() {
    events.saveAll(
        List.of(aFailedLogon().by(upn).at(SUMMER_NOON.minus(Duration.ofDays(1))).build()));

    assertThat(baselines.priorTo(upn, SUMMER_NOON).entries()).isEmpty();
  }

  @Test
  void baselineLeavesOutTheCurrentDaySoAnEventCannotVouchForItself() {
    events.saveAll(List.of(aLogon().by(upn).at(SUMMER_NOON).build()));

    assertThat(baselines.priorTo(upn, SUMMER_NOON).entries()).isEmpty();
  }

  @Test
  void baselineCoversExactlyThirtyDays() {
    events.saveAll(
        List.of(
            aLogon().by(upn).on("day-30").at(SUMMER_NOON.minus(Duration.ofDays(30))).build(),
            aLogon().by(upn).on("day-31").at(SUMMER_NOON.minus(Duration.ofDays(31))).build()));

    assertThat(baselines.priorTo(upn, SUMMER_NOON).entries())
        .containsExactly(new UserBaseline.Entry(12, "day-30", 1));
  }

  @Test
  void redeliveringALogonDoesNotInflateTheBaseline() {
    var logon = aLogon().by(upn).at(SUMMER_NOON.minus(Duration.ofDays(1))).build();

    events.saveAll(List.of(logon));
    events.saveAll(List.of(logon));

    assertThat(baselines.priorTo(upn, SUMMER_NOON).totalLogons()).isEqualTo(1);
  }
}
