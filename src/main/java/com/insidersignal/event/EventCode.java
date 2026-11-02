package com.insidersignal.event;

import java.util.Arrays;

/** The Windows Security event IDs this service understands. */
public enum EventCode {
  LOGON_SUCCESS(4624),
  LOGON_FAILURE(4625),
  ADDED_TO_GLOBAL_GROUP(4728),
  ADDED_TO_LOCAL_GROUP(4732);

  private final int windowsId;

  EventCode(int windowsId) {
    this.windowsId = windowsId;
  }

  public int windowsId() {
    return windowsId;
  }

  public static EventCode fromWindowsId(int windowsId) {
    return Arrays.stream(values())
        .filter(code -> code.windowsId == windowsId)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown Windows event " + windowsId));
  }
}
