package com.insidersignal;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/** Real Postgres, ClickHouse and Kafka, started once and shared by every integration test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @ServiceConnection
  static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

  static final ClickHouseContainer CLICKHOUSE =
      new ClickHouseContainer("clickhouse/clickhouse-server:25.8-alpine");

  static {
    POSTGRES.start();
    KAFKA.start();
    CLICKHOUSE.start();
  }

  @DynamicPropertySource
  static void clickHouseProperties(DynamicPropertyRegistry registry) {
    registry.add("insider-signal.clickhouse.url", CLICKHOUSE::getJdbcUrl);
    registry.add("insider-signal.clickhouse.username", CLICKHOUSE::getUsername);
    registry.add("insider-signal.clickhouse.password", CLICKHOUSE::getPassword);
  }
}
