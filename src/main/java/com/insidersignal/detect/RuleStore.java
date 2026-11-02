package com.insidersignal.detect;

import java.time.Instant;
import java.util.List;

/** Durable storage for rule configuration. */
public interface RuleStore {

  Rule find(RuleId id);

  List<Rule> findAll();

  Rule update(RuleId id, RuleConfig config, Instant updatedAt);
}
