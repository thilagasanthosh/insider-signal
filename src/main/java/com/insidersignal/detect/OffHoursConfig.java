package com.insidersignal.detect;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param minBaselineLogons logons a user needs in the baseline before this rule applies
 * @param minLogonsInHour logons in an hour of day for that hour to count as usual for the user
 * @param score amount added to the user's risk score
 */
public record OffHoursConfig(
    @Min(1) int minBaselineLogons, @Min(1) int minLogonsInHour, @Min(0) @Max(100) int score)
    implements RuleConfig {}
