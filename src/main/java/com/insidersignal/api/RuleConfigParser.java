package com.insidersignal.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insidersignal.detect.RuleConfig;
import com.insidersignal.detect.RuleId;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Turns the contract's free-form config object into the rule's typed, validated settings. */
@Component
public class RuleConfigParser {

  private final ObjectMapper strictMapper;
  private final Validator validator;

  public RuleConfigParser(ObjectMapper mapper, Validator validator) {
    this.strictMapper =
        mapper
            .copy()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    this.validator = validator;
  }

  public RuleConfig parse(RuleId id, Map<String, Object> raw) {
    var config = convert(id, raw);
    var errors = new TreeMap<String, String>();
    validator
        .validate(config)
        .forEach(v -> errors.put("config." + v.getPropertyPath(), v.getMessage()));
    if (!errors.isEmpty()) {
      throw new InvalidRuleConfigException(errors);
    }
    return config;
  }

  private RuleConfig convert(RuleId id, Map<String, Object> raw) {
    try {
      return strictMapper.convertValue(raw, id.configType());
    } catch (IllegalArgumentException e) {
      var expected =
          Arrays.stream(id.configType().getRecordComponents())
              .map(component -> component.getName())
              .collect(Collectors.joining(", "));
      throw new InvalidRuleConfigException(
          Map.of("config", "%s needs integers: %s".formatted(id.publicName(), expected)));
    }
  }
}
