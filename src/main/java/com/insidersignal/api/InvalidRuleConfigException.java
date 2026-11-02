package com.insidersignal.api;

import java.util.Map;

class InvalidRuleConfigException extends RuntimeException {

  private final Map<String, String> errors;

  InvalidRuleConfigException(Map<String, String> errors) {
    super("Invalid rule configuration");
    this.errors = errors;
  }

  Map<String, String> errors() {
    return errors;
  }
}
