package com.insidersignal.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * ClickHouse sits beside the primary Postgres connection: {@code defaultCandidate = false} leaves
 * Spring Boot's Postgres auto-configuration untouched. Flyway does not support ClickHouse, so the
 * idempotent schema is applied here, before anything can use the connection (ADR 0002).
 */
@Configuration
public class ClickHouseConfig {

  public static final String CLICKHOUSE = "clickhouse";

  @Bean(defaultCandidate = false)
  @Qualifier(CLICKHOUSE)
  HikariDataSource clickHouseDataSource(InsiderSignalProperties properties) {
    var dataSource = new HikariDataSource();
    dataSource.setPoolName("clickhouse");
    dataSource.setJdbcUrl(properties.clickhouse().url());
    dataSource.setUsername(properties.clickhouse().username());
    dataSource.setPassword(properties.clickhouse().password());
    return dataSource;
  }

  @Bean(defaultCandidate = false)
  @Qualifier(CLICKHOUSE)
  JdbcTemplate clickHouseJdbc(@Qualifier(CLICKHOUSE) DataSource dataSource) {
    new ResourceDatabasePopulator(new ClassPathResource("db/clickhouse/schema.sql"))
        .execute(dataSource);
    return new JdbcTemplate(dataSource);
  }
}
