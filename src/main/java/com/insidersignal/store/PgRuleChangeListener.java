package com.insidersignal.store;

import com.insidersignal.detect.RuleCatalog;
import com.insidersignal.detect.RuleId;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Evicts cached rules when any instance changes them, via Postgres LISTEN/NOTIFY. Notifications
 * sent while disconnected are lost, so every (re)connect evicts everything.
 */
@Component
public class PgRuleChangeListener implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(PgRuleChangeListener.class);
  private static final int POLL_MILLIS = 1_000;
  private static final Duration RECONNECT_DELAY = Duration.ofSeconds(5);

  private final DataSource dataSource;
  private final RuleCatalog catalog;
  private volatile boolean running;
  private Thread thread;

  public PgRuleChangeListener(DataSource dataSource, RuleCatalog catalog) {
    this.dataSource = dataSource;
    this.catalog = catalog;
  }

  @Override
  public void start() {
    running = true;
    thread = Thread.ofVirtual().name("rule-change-listener").start(this::listenUntilStopped);
  }

  @Override
  public void stop() {
    running = false;
    thread.interrupt();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void listenUntilStopped() {
    while (running) {
      try (var connection = dataSource.getConnection()) {
        listen(connection);
      } catch (SQLException e) {
        log.warn("Lost rule-change connection; reconnecting", e);
        pauseBeforeReconnect();
      }
    }
  }

  private void listen(Connection connection) throws SQLException {
    try (var statement = connection.createStatement()) {
      statement.execute("LISTEN rule_changed");
    }
    catalog.invalidateAll();
    var notifications = connection.unwrap(PGConnection.class);
    while (running) {
      var received = notifications.getNotifications(POLL_MILLIS);
      if (received != null) {
        for (var notification : received) {
          evict(notification.getParameter());
        }
      }
    }
  }

  private void evict(String ruleName) {
    log.info("Rule changed, evicting from cache: {}", ruleName);
    RuleId.fromName(ruleName).ifPresentOrElse(catalog::invalidate, catalog::invalidateAll);
  }

  private void pauseBeforeReconnect() {
    try {
      Thread.sleep(RECONNECT_DELAY);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      running = false;
    }
  }
}
