package com.insidersignal.detect;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param failureThreshold failed logons needed before a success counts as a brute-force success
 * @param windowMinutes how far back from the successful logon failures are counted
 * @param score amount added to the user's risk score
 */
public record BruteForceConfig(
    @Min(1) int failureThreshold, @Min(1) @Max(1440) int windowMinutes, @Min(0) @Max(100) int score)
    implements RuleConfig {}
