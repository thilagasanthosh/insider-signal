package com.insidersignal.detect;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @param minBaselineLogons logons a user needs in the baseline before this rule applies
 * @param score amount added to the user's risk score
 */
public record NewHostConfig(@Min(1) int minBaselineLogons, @Min(0) @Max(100) int score)
    implements RuleConfig {}
