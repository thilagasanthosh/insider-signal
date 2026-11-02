package com.insidersignal.config;

import com.insidersignal.detect.AlertStore;
import com.insidersignal.detect.BaselineStore;
import com.insidersignal.detect.BruteForceRule;
import com.insidersignal.detect.EventProcessor;
import com.insidersignal.detect.EventStore;
import com.insidersignal.detect.LogonRule;
import com.insidersignal.detect.NewHostRule;
import com.insidersignal.detect.OffHoursRule;
import com.insidersignal.detect.RiskService;
import com.insidersignal.detect.RuleCatalog;
import com.insidersignal.detect.RuleStore;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the framework-free detection code. A new rule only needs a new bean here. */
@Configuration
public class DetectionConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  RuleCatalog ruleCatalog(RuleStore store, Clock clock, InsiderSignalProperties properties) {
    return new RuleCatalog(store, clock, properties.rules().cacheTtl());
  }

  @Bean
  LogonRule bruteForceRule(EventStore events, RuleCatalog rules) {
    return new BruteForceRule(events, rules);
  }

  @Bean
  LogonRule offHoursRule(BaselineStore baselines, RuleCatalog rules) {
    return new OffHoursRule(baselines, rules);
  }

  @Bean
  LogonRule newHostRule(BaselineStore baselines, RuleCatalog rules) {
    return new NewHostRule(baselines, rules);
  }

  @Bean
  EventProcessor eventProcessor(
      EventStore events,
      AlertStore alerts,
      List<LogonRule> rules,
      Clock clock,
      InsiderSignalProperties properties) {
    return new EventProcessor(events, alerts, rules, clock, properties.detection().maxEventAge());
  }

  @Bean
  RiskService riskService(AlertStore alerts, Clock clock, InsiderSignalProperties properties) {
    return new RiskService(alerts, clock, properties.risk().window());
  }
}
