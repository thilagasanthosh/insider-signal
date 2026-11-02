package com.insidersignal.ingest;

import com.insidersignal.detect.EventProcessor;
import com.insidersignal.event.WinEvent;
import java.util.List;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

  private final EventProcessor processor;

  public KafkaEventConsumer(EventProcessor processor) {
    this.processor = processor;
  }

  /** Offsets are committed after this returns, so a crash redelivers the batch. */
  @KafkaListener(topics = "${insider-signal.kafka.events-topic}")
  public void onBatch(List<ConsumerRecord<String, WinEvent>> records) {
    records.stream().filter(record -> record.value() == null).forEach(KafkaEventConsumer::logSkip);
    var events = records.stream().map(ConsumerRecord::value).filter(Objects::nonNull).toList();
    processor.process(events);
  }

  private static void logSkip(ConsumerRecord<String, WinEvent> record) {
    log.warn(
        "Skipping unreadable record at {}-{} offset {}",
        record.topic(),
        record.partition(),
        record.offset());
  }
}
