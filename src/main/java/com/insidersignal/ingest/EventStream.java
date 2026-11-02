package com.insidersignal.ingest;

import com.insidersignal.event.WinEvent;
import java.util.List;

/** Where accepted events go for asynchronous processing. */
public interface EventStream {

  /**
   * Returns once the whole batch is durable.
   *
   * @throws EventStreamUnavailableException if it could not be made durable; callers may retry
   */
  void publish(List<WinEvent> events);
}
