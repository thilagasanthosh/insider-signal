package com.insidersignal.detect;

import java.util.Arrays;
import java.util.Optional;

/** Detection rules, each knowing its public name and configuration type. */
public enum RuleId {
  BRUTE_FORCE("brute-force", BruteForceConfig.class),
  OFF_HOURS("off-hours", OffHoursConfig.class),
  NEW_HOST("new-host", NewHostConfig.class);

  private final String publicName;
  private final Class<? extends RuleConfig> configType;

  RuleId(String publicName, Class<? extends RuleConfig> configType) {
    this.publicName = publicName;
    this.configType = configType;
  }

  public String publicName() {
    return publicName;
  }

  public Class<? extends RuleConfig> configType() {
    return configType;
  }

  public static Optional<RuleId> fromName(String publicName) {
    return Arrays.stream(values()).filter(id -> id.publicName.equals(publicName)).findFirst();
  }
}
