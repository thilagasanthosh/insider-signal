package com.insidersignal.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insidersignal.detect.Alert;
import com.insidersignal.detect.AlertStore;
import com.insidersignal.detect.RuleId;
import com.insidersignal.detect.Severity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresAlertStore implements AlertStore {

  private static final String COLUMNS =
      "id, rule_id, upn, severity, score, occurred_at, detail->>'reason' AS reason, detail";

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;

  public PostgresAlertStore(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  private record Detail(String reason, List<String> evidenceEventIds) {}

  @Override
  public void save(Alert alert) {
    jdbc.sql(
            """
            INSERT INTO alert (id, rule_id, upn, severity, score, occurred_at, detail)
            VALUES (:id, :ruleId, :upn, :severity, :score, :occurredAt, CAST(:detail AS jsonb))
            ON CONFLICT (id) DO NOTHING
            """)
        .param("id", alert.id())
        .param("ruleId", alert.ruleId().publicName())
        .param("upn", alert.upn())
        .param("severity", alert.severity().name())
        .param("score", alert.score())
        .param("occurredAt", Timestamp.from(alert.occurredAt()))
        .param("detail", toJson(new Detail(alert.reason(), alert.evidenceEventIds())))
        .update();
  }

  @Override
  public List<Alert> latest(String upn, int limit) {
    return jdbc.sql(
            """
            SELECT %s FROM alert
            WHERE CAST(:upn AS text) IS NULL OR upn = :upn
            ORDER BY occurred_at DESC, id
            LIMIT :limit
            """
                .formatted(COLUMNS))
        .param("upn", upn, java.sql.Types.VARCHAR)
        .param("limit", limit)
        .query(this::toAlert)
        .list();
  }

  @Override
  public List<Alert> forUserSince(String upn, Instant since) {
    return jdbc.sql(
            """
            SELECT %s FROM alert
            WHERE upn = :upn AND occurred_at >= :since
            ORDER BY occurred_at, id
            """
                .formatted(COLUMNS))
        .param("upn", upn)
        .param("since", Timestamp.from(since))
        .query(this::toAlert)
        .list();
  }

  private Alert toAlert(ResultSet row, int rowNumber) throws SQLException {
    var detail = fromJson(row.getString("detail"));
    return new Alert(
        row.getString("id"),
        RuleId.fromName(row.getString("rule_id")).orElseThrow(),
        row.getString("upn"),
        Severity.valueOf(row.getString("severity")),
        row.getInt("score"),
        row.getTimestamp("occurred_at").toInstant(),
        detail.reason(),
        detail.evidenceEventIds());
  }

  private String toJson(Detail detail) {
    try {
      return mapper.writeValueAsString(detail);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialise alert detail", e);
    }
  }

  private Detail fromJson(String json) {
    try {
      return mapper.readValue(json, Detail.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not read alert detail", e);
    }
  }
}
