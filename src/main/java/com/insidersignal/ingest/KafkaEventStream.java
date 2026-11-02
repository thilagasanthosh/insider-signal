package com.insidersignal.ingest;

import com.insidersignal.config.InsiderSignalProperties;
import com.insidersignal.event.WinEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Keys by user, so one user's events stay in order within a partition. */
@Component
public class KafkaEventStream implements EventStream {

  private static final long ACK_TIMEOUT_SECONDS = 15;

  private final KafkaTemplate<String, WinEvent> kafka;
  private final String topic;

  public KafkaEventStream(
      KafkaTemplate<String, WinEvent> kafka, InsiderSignalProperties properties) {
    this.kafka = kafka;
    this.topic = properties.kafka().eventsTopic();
  }

  @Override
  public void publish(List<WinEvent> events) {
    try {
      var sends =
          events.stream()
              .map(event -> kafka.send(topic, event.upn(), event))
              .toArray(CompletableFuture[]::new);
      CompletableFuture.allOf(sends).get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (KafkaException | ExecutionException | TimeoutException e) {
      throw new EventStreamUnavailableException("Kafka did not acknowledge the batch", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new EventStreamUnavailableException("Interrupted while publishing", e);
    }
  }
}
