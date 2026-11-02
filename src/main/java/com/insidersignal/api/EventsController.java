package com.insidersignal.api;

import com.insidersignal.api.generated.EventBatchDto;
import com.insidersignal.api.generated.EventsApi;
import com.insidersignal.api.generated.IngestAcceptedDto;
import com.insidersignal.api.generated.WinEventDto;
import com.insidersignal.event.EventCode;
import com.insidersignal.event.WinEvent;
import com.insidersignal.ingest.EventStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventsController implements EventsApi {

  private final EventStream stream;

  public EventsController(EventStream stream) {
    this.stream = stream;
  }

  @Override
  public ResponseEntity<IngestAcceptedDto> ingestEvents(EventBatchDto batch) {
    var events = batch.getEvents().stream().map(EventsController::toEvent).toList();
    stream.publish(events);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(new IngestAcceptedDto(events.size()));
  }

  private static WinEvent toEvent(WinEventDto dto) {
    return new WinEvent(
        dto.getEventId(),
        EventCode.fromWindowsId(dto.getEventCode().getValue()),
        dto.getOccurredAt().toInstant(),
        dto.getUpn(),
        dto.getHost(),
        dto.getTargetGroup());
  }
}
