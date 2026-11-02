package com.insidersignal.detect;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** In-memory {@link RuleStore} seeded with the same defaults as the Flyway migration. */
public class FakeRuleStore implements RuleStore {

  private final Map<RuleId, Rule> rules = new EnumMap<>(RuleId.class);
  private int loads;

  public FakeRuleStore() {
    set(RuleId.BRUTE_FORCE, new BruteForceConfig(5, 5, 60));
    set(RuleId.OFF_HOURS, new OffHoursConfig(20, 1, 30));
    set(RuleId.NEW_HOST, new NewHostConfig(20, 30));
  }

  /** Changes a rule behind the cache's back, as another instance would. */
  public void set(RuleId id, RuleConfig config) {
    rules.put(id, new Rule(id, config, Instant.EPOCH));
  }

  public int loads() {
    return loads;
  }

  @Override
  public Rule find(RuleId id) {
    loads++;
    return rules.get(id);
  }

  @Override
  public List<Rule> findAll() {
    return List.copyOf(rules.values());
  }

  @Override
  public Rule update(RuleId id, RuleConfig config, Instant updatedAt) {
    var rule = new Rule(id, config, updatedAt);
    rules.put(id, rule);
    return rule;
  }
}
