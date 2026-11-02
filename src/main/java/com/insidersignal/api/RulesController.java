package com.insidersignal.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insidersignal.api.generated.RuleDto;
import com.insidersignal.api.generated.RuleUpdateDto;
import com.insidersignal.api.generated.RulesApi;
import com.insidersignal.detect.Rule;
import com.insidersignal.detect.RuleCatalog;
import com.insidersignal.detect.RuleId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class RulesController implements RulesApi {

  private final RuleCatalog catalog;
  private final RuleConfigParser parser;
  private final ObjectMapper mapper;

  public RulesController(RuleCatalog catalog, RuleConfigParser parser, ObjectMapper mapper) {
    this.catalog = catalog;
    this.parser = parser;
    this.mapper = mapper;
  }

  @Override
  public ResponseEntity<List<RuleDto>> listRules() {
    return ResponseEntity.ok(catalog.all().stream().map(this::toDto).toList());
  }

  @Override
  public ResponseEntity<RuleDto> updateRule(String ruleId, RuleUpdateDto update) {
    var id =
        RuleId.fromName(ruleId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown rule: " + ruleId));
    var config = parser.parse(id, update.getConfig());
    return ResponseEntity.ok(toDto(catalog.update(id, config)));
  }

  private RuleDto toDto(Rule rule) {
    Map<String, Object> config = mapper.convertValue(rule.config(), new TypeReference<>() {});
    return new RuleDto(rule.id().publicName(), config, rule.updatedAt().atOffset(ZoneOffset.UTC));
  }
}
