package com.insidersignal.api;

import com.insidersignal.api.generated.RiskReasonDto;
import com.insidersignal.api.generated.UserRiskDto;
import com.insidersignal.api.generated.UsersApi;
import com.insidersignal.detect.Alert;
import com.insidersignal.detect.RiskService;
import java.time.ZoneOffset;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsersController implements UsersApi {

  private final RiskService risk;

  public UsersController(RiskService risk) {
    this.risk = risk;
  }

  @Override
  public ResponseEntity<UserRiskDto> getUserRisk(String upn) {
    var userRisk = risk.riskFor(upn);
    var reasons = userRisk.alerts().stream().map(UsersController::toReason).toList();
    return ResponseEntity.ok(new UserRiskDto(userRisk.upn(), userRisk.score(), reasons));
  }

  private static RiskReasonDto toReason(Alert alert) {
    return new RiskReasonDto(
        alert.ruleId().publicName(),
        alert.score(),
        alert.occurredAt().atOffset(ZoneOffset.UTC),
        alert.reason());
  }
}
