package com.insidersignal.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WinEventTest {

  @Test
  void normalisesUpnAndHostToLowerCaseBecauseAdNamesAreCaseInsensitive() {
    var event =
        new WinEvent(
            "e1", EventCode.LOGON_SUCCESS, Instant.EPOCH, "Alice@Contoso.Example", "WKS-01", null);

    assertThat(event.upn()).isEqualTo("alice@contoso.example");
    assertThat(event.host()).isEqualTo("wks-01");
  }

  @Test
  void mapsWindowsEventIdsToEventCodes() {
    assertThat(EventCode.fromWindowsId(4624)).isEqualTo(EventCode.LOGON_SUCCESS);
    assertThat(EventCode.fromWindowsId(4625)).isEqualTo(EventCode.LOGON_FAILURE);
    assertThat(EventCode.fromWindowsId(4728)).isEqualTo(EventCode.ADDED_TO_GLOBAL_GROUP);
    assertThat(EventCode.fromWindowsId(4732)).isEqualTo(EventCode.ADDED_TO_LOCAL_GROUP);
  }

  @Test
  void rejectsUnknownWindowsEventId() {
    assertThatThrownBy(() -> EventCode.fromWindowsId(1102))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
