package com.insidersignal.store;

import com.insidersignal.config.ClickHouseConfig;
import com.insidersignal.detect.EventStore;
import com.insidersignal.event.EventCode;
import com.insidersignal.event.WinEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClickHouseEventStore implements EventStore {

  private static final String INSERT =
      """
      INSERT INTO win_events (event_id, event_code, occurred_at, upn, host, target_group)
      VALUES (?, ?, ?, ?, ?, ?)
      """;

  private static final String LOGON_ATTEMPTS =
      """
      SELECT event_id, event_code, toUnixTimestamp64Milli(occurred_at) AS occurred_ms,
             upn, host, target_group
      FROM win_events FINAL
      WHERE upn = ?
        AND event_code IN (4624, 4625)
        AND occurred_at BETWEEN fromUnixTimestamp64Milli(?) AND fromUnixTimestamp64Milli(?)
      ORDER BY occurred_at, event_id
      """;

  private final JdbcTemplate jdbc;

  public ClickHouseEventStore(@Qualifier(ClickHouseConfig.CLICKHOUSE) JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void saveAll(List<WinEvent> events) {
    jdbc.batchUpdate(
        INSERT,
        events,
        events.size(),
        (statement, event) -> {
          statement.setString(1, event.eventId());
          statement.setInt(2, event.code().windowsId());
          statement.setObject(3, event.occurredAt());
          statement.setString(4, event.upn());
          statement.setString(5, event.host());
          statement.setString(6, event.targetGroup());
        });
  }

  @Override
  public List<WinEvent> logonAttempts(String upn, Instant from, Instant to) {
    return jdbc.query(
        LOGON_ATTEMPTS, ClickHouseEventStore::toEvent, upn, from.toEpochMilli(), to.toEpochMilli());
  }

  private static WinEvent toEvent(ResultSet row, int rowNumber) throws SQLException {
    return new WinEvent(
        row.getString("event_id"),
        EventCode.fromWindowsId(row.getInt("event_code")),
        Instant.ofEpochMilli(row.getLong("occurred_ms")),
        row.getString("upn"),
        row.getString("host"),
        row.getString("target_group"));
  }
}
