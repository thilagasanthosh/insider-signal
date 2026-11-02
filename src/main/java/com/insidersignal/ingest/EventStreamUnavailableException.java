package com.insidersignal.ingest;

public class EventStreamUnavailableException extends RuntimeException {

  public EventStreamUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
