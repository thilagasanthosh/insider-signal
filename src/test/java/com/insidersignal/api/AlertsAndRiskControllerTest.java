package com.insidersignal.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insidersignal.detect.Alert;
import com.insidersignal.detect.AlertStore;
import com.insidersignal.detect.RiskService;
import com.insidersignal.detect.RuleId;
import com.insidersignal.detect.Severity;
import com.insidersignal.detect.UserRisk;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AlertsController.class, UsersController.class})
class AlertsAndRiskControllerTest {

  private static final String UPN = "alice@contoso.example";
  private static final Alert ALERT =
      new Alert(
          "off-hours:e1",
          RuleId.OFF_HOURS,
          UPN,
          Severity.MEDIUM,
          30,
          Instant.parse("2026-06-15T02:00:00Z"),
          "Logon at 02:00 UTC",
          List.of("e1"));

  @Autowired private MockMvc mvc;
  @MockitoBean private AlertStore alerts;
  @MockitoBean private RiskService risk;

  @Test
  void listsAlertsWithEvidence() throws Exception {
    when(alerts.latest(null, 50)).thenReturn(List.of(ALERT));

    mvc.perform(get("/api/v1/alerts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("off-hours:e1"))
        .andExpect(jsonPath("$[0].ruleId").value("off-hours"))
        .andExpect(jsonPath("$[0].severity").value("MEDIUM"))
        .andExpect(jsonPath("$[0].evidenceEventIds[0]").value("e1"));
  }

  @Test
  void filtersAlertsByUserCaseInsensitively() throws Exception {
    mvc.perform(get("/api/v1/alerts").param("upn", "Alice@Contoso.Example").param("limit", "5"))
        .andExpect(status().isOk());

    verify(alerts).latest(UPN, 5);
  }

  @Test
  void rejectsAnOutOfRangeLimit() throws Exception {
    mvc.perform(get("/api/v1/alerts").param("limit", "0")).andExpect(status().isBadRequest());
  }

  @Test
  void returnsScoreAndReasons() throws Exception {
    when(risk.riskFor(UPN)).thenReturn(new UserRisk(UPN, 30, List.of(ALERT)));

    mvc.perform(get("/api/v1/users/{upn}/risk", UPN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.upn").value(UPN))
        .andExpect(jsonPath("$.score").value(30))
        .andExpect(jsonPath("$.reasons[0].ruleId").value("off-hours"))
        .andExpect(jsonPath("$.reasons[0].reason").value("Logon at 02:00 UTC"));
  }
}
