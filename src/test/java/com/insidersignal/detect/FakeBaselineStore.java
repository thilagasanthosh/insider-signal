package com.insidersignal.detect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FakeBaselineStore implements BaselineStore {

  private final List<UserBaseline.Entry> entries = new ArrayList<>();

  public FakeBaselineStore with(int hour, String host, long logons) {
    entries.add(new UserBaseline.Entry(hour, host, logons));
    return this;
  }

  @Override
  public UserBaseline priorTo(String upn, Instant at) {
    return new UserBaseline(entries);
  }
}
