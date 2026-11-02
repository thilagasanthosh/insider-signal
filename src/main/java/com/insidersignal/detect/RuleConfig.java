package com.insidersignal.detect;

/** Marker for a rule's tunable settings, stored as JSONB. */
public sealed interface RuleConfig permits BruteForceConfig, OffHoursConfig, NewHostConfig {}
