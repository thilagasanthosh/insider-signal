package com.insidersignal.detect;

import com.insidersignal.event.WinEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FakeEventStore implements EventStore {

  private final List<WinEvent> events = new ArrayList<>();

  public void add(WinEvent... newEvents) {
    events.addAll(List.of(newEvents));
  }

  public List<WinEvent> saved() {
    return events;
  }

  @Override
  public void saveAll(List<WinEvent> newEvents) {
    events.addAll(newEvents);
  }

  @Override
  public List<WinEvent> logonAttempts(String upn, Instant from, Instant to) {
    return events.stream()
        .filter(e -> e.upn().equals(upn))
        .filter(e -> e.isFailedLogon() || e.isSuccessfulLogon())
        .filter(e -> !e.occurredAt().isBefore(from) && !e.occurredAt().isAfter(to))
        .sorted(Comparator.comparing(WinEvent::occurredAt).thenComparing(WinEvent::eventId))
        .toList();
  }
}
