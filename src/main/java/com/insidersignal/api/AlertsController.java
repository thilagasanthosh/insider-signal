package com.insidersignal.api;

import com.insidersignal.api.generated.AlertDto;
import com.insidersignal.api.generated.AlertsApi;
import com.insidersignal.detect.Alert;
import com.insidersignal.detect.AlertStore;
import com.insidersignal.event.WinEvent;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlertsController implements AlertsApi {

  private final AlertStore alerts;

  public AlertsController(AlertStore alerts) {
    this.alerts = alerts;
  }

  @Override
  public ResponseEntity<List<AlertDto>> listAlerts(String upn, Integer limit) {
    var normalisedUpn = upn == null ? null : WinEvent.normalise(upn);
    return ResponseEntity.ok(
        alerts.latest(normalisedUpn, limit).stream().map(AlertsController::toDto).toList());
  }

  private static AlertDto toDto(Alert alert) {
    return new AlertDto(
        alert.id(),
        alert.ruleId().publicName(),
        alert.upn(),
        AlertDto.SeverityEnum.fromValue(alert.severity().name()),
        alert.score(),
        alert.occurredAt().atOffset(ZoneOffset.UTC),
        alert.reason(),
        alert.evidenceEventIds());
  }
}
