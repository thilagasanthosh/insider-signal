package com.insidersignal.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

  @Bean
  NewTopic eventsTopic(InsiderSignalProperties properties) {
    return TopicBuilder.name(properties.kafka().eventsTopic())
        .partitions(properties.kafka().eventsPartitions())
        .build();
  }

  /**
   * Retries a failed batch for as long as it takes, with no dead-letter topic. Failures here mean a
   * store is down; the events are safe in Kafka, and consumption resumes when the store recovers.
   */
  @Bean
  CommonErrorHandler kafkaErrorHandler() {
    var backOff = new ExponentialBackOff(1_000, 2);
    backOff.setMaxInterval(30_000);
    return new DefaultErrorHandler(backOff);
  }
}
