package com.insidersignal.store;

import com.insidersignal.config.ClickHouseConfig;
import com.insidersignal.detect.BaselineStore;
import com.insidersignal.detect.UserBaseline;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClickHouseBaselineStore implements BaselineStore {

  private static final int BASELINE_DAYS = 30;

  private static final String BASELINE =
      """
      SELECT hour, host, uniqExactMerge(logons) AS logons
      FROM user_logon_baseline
      WHERE upn = ? AND day >= ? AND day < ?
      GROUP BY hour, host
      """;

  private final JdbcTemplate jdbc;

  public ClickHouseBaselineStore(@Qualifier(ClickHouseConfig.CLICKHOUSE) JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public UserBaseline priorTo(String upn, Instant at) {
    var day = at.atZone(ZoneOffset.UTC).toLocalDate();
    var entries =
        jdbc.query(
            BASELINE,
            (row, rowNumber) ->
                new UserBaseline.Entry(
                    row.getInt("hour"), row.getString("host"), row.getLong("logons")),
            upn,
            day.minusDays(BASELINE_DAYS).toString(),
            day.toString());
    return new UserBaseline(entries);
  }
}
