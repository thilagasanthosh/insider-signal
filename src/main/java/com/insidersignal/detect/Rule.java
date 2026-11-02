package com.insidersignal.detect;

import java.time.Instant;

public record Rule(RuleId id, RuleConfig config, Instant updatedAt) {}
