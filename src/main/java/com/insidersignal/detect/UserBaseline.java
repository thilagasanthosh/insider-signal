package com.insidersignal.detect;

import java.util.List;

/** A user's normal logon pattern: how often they logged on, per hour of day and host. */
public record UserBaseline(List<Entry> entries) {

  public record Entry(int hour, String host, long logons) {}

  public long totalLogons() {
    return entries.stream().mapToLong(Entry::logons).sum();
  }

  public long logonsInHour(int hour) {
    return entries.stream().filter(e -> e.hour() == hour).mapToLong(Entry::logons).sum();
  }

  public boolean hasUsedHost(String host) {
    return entries.stream().anyMatch(e -> e.host().equals(host));
  }
}
