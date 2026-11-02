package com.insidersignal.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insidersignal.detect.OffHoursConfig;
import com.insidersignal.detect.Rule;
import com.insidersignal.detect.RuleCatalog;
import com.insidersignal.detect.RuleId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RulesController.class)
@Import(RuleConfigParser.class)
class RulesControllerTest {

  private static final Instant UPDATED = Instant.parse("2026-06-15T10:00:00Z");
  private static final OffHoursConfig CONFIG = new OffHoursConfig(20, 5, 30);

  @Autowired private MockMvc mvc;
  @MockitoBean private RuleCatalog catalog;

  private org.springframework.test.web.servlet.ResultActions putOffHours(String config)
      throws Exception {
    return mvc.perform(
        put("/api/v1/rules/off-hours")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"config\":" + config + "}"));
  }

  @Test
  void listsRulesWithTheirConfig() throws Exception {
    when(catalog.all()).thenReturn(List.of(new Rule(RuleId.OFF_HOURS, CONFIG, UPDATED)));

    mvc.perform(get("/api/v1/rules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("off-hours"))
        .andExpect(jsonPath("$[0].config.minLogonsInHour").value(5));
  }

  @Test
  void updatesARule() throws Exception {
    when(catalog.update(RuleId.OFF_HOURS, CONFIG))
        .thenReturn(new Rule(RuleId.OFF_HOURS, CONFIG, UPDATED));

    putOffHours("{\"minBaselineLogons\":20,\"minLogonsInHour\":5,\"score\":30}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.config.minLogonsInHour").value(5));

    verify(catalog).update(RuleId.OFF_HOURS, CONFIG);
  }

  @Test
  void returns404ForAnUnknownRule() throws Exception {
    mvc.perform(
            put("/api/v1/rules/nope")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"config\":{}}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void rejectsAnOutOfRangeValueWithFieldLevelProblem() throws Exception {
    putOffHours("{\"minBaselineLogons\":20,\"minLogonsInHour\":0,\"score\":30}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['config.minLogonsInHour']").exists());

    verify(catalog, never()).update(any(), any());
  }

  @Test
  void rejectsAPartialConfigRatherThanDefaultingMissingFieldsToZero() throws Exception {
    putOffHours("{\"minLogonsInHour\":5}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.config").exists());
  }

  @Test
  void rejectsAnUnknownField() throws Exception {
    putOffHours("{\"minBaselineLogons\":20,\"minLogonsInHour\":5,\"score\":30,\"extra\":1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.config").exists());
  }

  @Test
  void rejectsAFractionalValue() throws Exception {
    putOffHours("{\"minBaselineLogons\":20,\"minLogonsInHour\":1.5,\"score\":30}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.config").exists());
  }
}
