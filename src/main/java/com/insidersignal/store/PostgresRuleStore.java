package com.insidersignal.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insidersignal.detect.Rule;
import com.insidersignal.detect.RuleConfig;
import com.insidersignal.detect.RuleId;
import com.insidersignal.detect.RuleStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresRuleStore implements RuleStore {

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;

  public PostgresRuleStore(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public Rule find(RuleId id) {
    return jdbc.sql("SELECT id, config, updated_at FROM detection_rule WHERE id = ?")
        .param(id.publicName())
        .query(this::toRule)
        .single();
  }

  @Override
  public List<Rule> findAll() {
    return jdbc.sql("SELECT id, config, updated_at FROM detection_rule ORDER BY id")
        .query(this::toRule)
        .list();
  }

  @Override
  public Rule update(RuleId id, RuleConfig config, Instant updatedAt) {
    return jdbc.sql(
            """
            UPDATE detection_rule
            SET config = CAST(:config AS jsonb), updated_at = :updatedAt
            WHERE id = :id
            RETURNING id, config, updated_at
            """)
        .param("config", toJson(config))
        .param("updatedAt", Timestamp.from(updatedAt))
        .param("id", id.publicName())
        .query(this::toRule)
        .single();
  }

  private Rule toRule(ResultSet row, int rowNumber) throws SQLException {
    var id = RuleId.fromName(row.getString("id")).orElseThrow();
    return new Rule(
        id, fromJson(row.getString("config"), id), row.getTimestamp("updated_at").toInstant());
  }

  private String toJson(RuleConfig config) {
    try {
      return mapper.writeValueAsString(config);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialise rule config", e);
    }
  }

  private RuleConfig fromJson(String json, RuleId id) {
    try {
      return mapper.readValue(json, id.configType());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not read config of rule " + id.publicName(), e);
    }
  }
}
