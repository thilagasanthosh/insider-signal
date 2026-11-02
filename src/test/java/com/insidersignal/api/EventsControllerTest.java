package com.insidersignal.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.insidersignal.event.EventCode;
import com.insidersignal.event.WinEvent;
import com.insidersignal.ingest.EventStream;
import com.insidersignal.ingest.EventStreamUnavailableException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EventsController.class)
class EventsControllerTest {

  private static final String VALID_BATCH =
      """
      {"events":[{"eventId":"e1","eventCode":4625,"occurredAt":"2026-06-15T10:00:00+01:00",
        "upn":"Alice@Contoso.Example","host":"WKS-01"}]}
      """;

  @Autowired private MockMvc mvc;
  @MockitoBean private EventStream stream;

  @Test
  @SuppressWarnings("unchecked")
  void publishesAValidBatchAndReturns202() throws Exception {
    mvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(VALID_BATCH))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.accepted").value(1));

    ArgumentCaptor<List<WinEvent>> published = ArgumentCaptor.forClass(List.class);
    verify(stream).publish(published.capture());
    assertThat(published.getValue())
        .containsExactly(
            new WinEvent(
                "e1",
                EventCode.LOGON_FAILURE,
                Instant.parse("2026-06-15T09:00:00Z"),
                "alice@contoso.example",
                "wks-01",
                null));
  }

  @Test
  void rejectsAnEmptyBatchWithFieldLevelProblem() throws Exception {
    mvc.perform(
            post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"events\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errors.events").exists());

    verify(stream, never()).publish(any());
  }

  @Test
  void rejectsAnEventMissingARequiredField() throws Exception {
    var noUpn =
        """
        {"events":[{"eventId":"e1","eventCode":4625,"occurredAt":"2026-06-15T10:00:00Z",
          "host":"WKS-01"}]}
        """;

    mvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(noUpn))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors['events[0].upn']").exists());
  }

  @Test
  void rejectsAnUnsupportedEventCode() throws Exception {
    var badCode =
        """
        {"events":[{"eventId":"e1","eventCode":1102,"occurredAt":"2026-06-15T10:00:00Z",
          "upn":"a@b.example","host":"WKS-01"}]}
        """;

    mvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(badCode))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void returns503WhenTheStreamCannotTakeTheBatch() throws Exception {
    doThrow(new EventStreamUnavailableException("down", new RuntimeException()))
        .when(stream)
        .publish(any());

    mvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(VALID_BATCH))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
