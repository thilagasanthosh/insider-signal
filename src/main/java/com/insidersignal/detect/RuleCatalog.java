package com.insidersignal.detect;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Rule configuration, cached in process because every logon reads it. Other instances' updates
 * arrive through {@link #invalidate}; the TTL only bounds staleness if a notification is lost.
 */
public class RuleCatalog {

  private final RuleStore store;
  private final Clock clock;
  private final LoadingCache<RuleId, Rule> cache;

  public RuleCatalog(RuleStore store, Clock clock, Duration ttl) {
    this.store = store;
    this.clock = clock;
    this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).build(store::find);
  }

  public <T extends RuleConfig> T config(RuleId id, Class<T> type) {
    return type.cast(cache.get(id).config());
  }

  public List<Rule> all() {
    return store.findAll();
  }

  public Rule update(RuleId id, RuleConfig config) {
    var updated = store.update(id, config, clock.instant());
    invalidate(id);
    return updated;
  }

  public void invalidate(RuleId id) {
    cache.invalidate(id);
  }

  public void invalidateAll() {
    cache.invalidateAll();
  }
}
