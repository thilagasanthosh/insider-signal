package com.insidersignal.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("insider-signal")
public record InsiderSignalProperties(
    @Valid @NotNull Kafka kafka,
    @Valid @NotNull ClickHouse clickhouse,
    @Valid @NotNull Detection detection,
    @Valid @NotNull Risk risk,
    @Valid @NotNull Rules rules) {

  public record Kafka(@NotBlank String eventsTopic, @Min(1) int eventsPartitions) {}

  public record ClickHouse(@NotBlank String url, @NotBlank String username, String password) {}

  /**
   * @param maxEventAge events older than this build the baseline but raise no alerts, so replaying
   *     history cannot flood analysts with alerts about the past
   */
  public record Detection(@NotNull Duration maxEventAge) {}

  /**
   * @param window how long an alert keeps counting towards a user's risk score
   */
  public record Risk(@NotNull Duration window) {}

  /**
   * @param cacheTtl upper bound on staleness if a rule-change notification is lost
   */
  public record Rules(@NotNull Duration cacheTtl) {}
}
