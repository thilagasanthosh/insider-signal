import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays realistic Windows logon traffic with fictional users. Standard library only, so it runs
 * straight from source:
 *
 * <pre>
 * java tools/event-generator/EventGenerator.java history
 * java tools/event-generator/EventGenerator.java traffic
 * java tools/event-generator/EventGenerator.java attack dave@contoso.example
 * java tools/event-generator/EventGenerator.java probe alice@contoso.example wks-alice-01
 * </pre>
 *
 * Set BASE_URL to target somewhere other than http://localhost:8090.
 */
public class EventGenerator {

  private static final String ALICE = "alice@contoso.example";
  private static final String BOB = "bob@contoso.example";
  private static final String CAROL = "carol@contoso.example";
  private static final String DAVE = "dave@contoso.example";
  private static final int LOGON = 4624;
  private static final int FAILED_LOGON = 4625;
  private static final int BATCH_SIZE = 500;
  private static final int RARE_HOUR_LOGONS = 3;

  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final String baseUrl;
  private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

  private EventGenerator(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  private record Event(String id, int code, Instant at, String upn, String host) {
    String toJson() {
      return """
          {"eventId":"%s","eventCode":%d,"occurredAt":"%s","upn":"%s","host":"%s"}"""
          .formatted(id, code, at, upn, host);
    }
  }

  public static void main(String[] args) throws Exception {
    var baseUrl = System.getenv().getOrDefault("BASE_URL", "http://localhost:8090");
    var generator = new EventGenerator(baseUrl);
    var command = args.length > 0 ? args[0] : "";
    switch (command) {
      case "history" -> generator.history();
      case "traffic" -> generator.traffic();
      case "attack" -> generator.attack(args.length > 1 ? args[1] : DAVE);
      case "probe" -> generator.probe(args[1], args[2]);
      default -> {
        System.err.println("Usage: history | traffic | attack [upn] | probe <upn> <host>");
        System.exit(2);
      }
    }
  }

  /**
   * Thirty days of baseline, old enough to raise no alerts. Stable ids make it safe to re-run. Bob,
   * Carol and Dave work around the clock. Alice works a block of hours away from the current time, with
   * just a few logons in this hour and the next: usual today, off-hours once the rule is tightened.
   */
  private void history() throws Exception {
    var today = now.truncatedTo(ChronoUnit.DAYS);
    int currentHour = now.atZone(ZoneOffset.UTC).getHour();
    var events = new ArrayList<Event>();
    for (int daysAgo = 2; daysAgo <= 30; daysAgo++) {
      var day = today.minus(daysAgo, ChronoUnit.DAYS);
      for (int hour = 0; hour < 24; hour++) {
        events.add(historyEvent(BOB, "wks-bob-01", day, daysAgo, hour));
        events.add(historyEvent(CAROL, "wks-carol-01", day, daysAgo, hour));
        events.add(historyEvent(DAVE, "wks-dave-01", day, daysAgo, hour));
      }
      for (int offset = 6; offset < 14; offset++) {
        int hour = (currentHour + offset) % 24;
        events.add(historyEvent(ALICE, "wks-alice-01", day, daysAgo, hour));
      }
      if (daysAgo < 2 + RARE_HOUR_LOGONS) {
        events.add(historyEvent(ALICE, "wks-alice-01", day, daysAgo, currentHour));
        events.add(historyEvent(ALICE, "wks-alice-01", day, daysAgo, (currentHour + 1) % 24));
      }
    }
    send(events);
  }

  private static Event historyEvent(String upn, String host, Instant day, int daysAgo, int hour) {
    var id = "hist-%s-%s-%d".formatted(upn, day.toString().substring(0, 10), hour);
    return new Event(id, LOGON, day.plus(hour, ChronoUnit.HOURS), upn, host);
  }

  /**
   * Ordinary logons on usual hosts; nothing here should raise an alert. Dave is left out because a
   * success resets the brute-force count, which would mask the attack on him.
   */
  private void traffic() throws Exception {
    var events = new ArrayList<Event>();
    for (int i = 0; i < 20; i++) {
      var at = now.minusSeconds(i * 15L);
      events.add(new Event(uniqueId("traffic", i), LOGON, at, BOB, "wks-bob-01"));
      events.add(new Event(uniqueId("traffic-c", i), LOGON, at, CAROL, "wks-carol-01"));
    }
    send(events);
  }

  /** Eight failed logons in two minutes, then a success. */
  private void attack(String upn) throws Exception {
    var host = "wks-" + upn.substring(0, upn.indexOf('@')) + "-01";
    var events = new ArrayList<Event>();
    for (int i = 8; i >= 1; i--) {
      var at = now.minusSeconds(i * 15L);
      events.add(new Event(uniqueId("attack", i), FAILED_LOGON, at, upn, host));
    }
    events.add(new Event(uniqueId("attack", 0), LOGON, now, upn, host));
    send(events);
  }

  private void probe(String upn, String host) throws Exception {
    send(List.of(new Event(uniqueId("probe", 0), LOGON, now, upn, host)));
  }

  private String uniqueId(String prefix, int index) {
    return "%s-%d-%d".formatted(prefix, now.toEpochMilli(), index);
  }

  private void send(List<Event> events) throws Exception {
    for (int start = 0; start < events.size(); start += BATCH_SIZE) {
      var batch = events.subList(start, Math.min(start + BATCH_SIZE, events.size()));
      var json = batch.stream().map(Event::toJson).toList();
      var body = "{\"events\":[" + String.join(",", json) + "]}";
      var request =
          HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/events"))
              .header("Content-Type", "application/json")
              .timeout(Duration.ofSeconds(30))
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 202) {
        throw new IllegalStateException(
            "HTTP %d: %s".formatted(response.statusCode(), response.body()));
      }
    }
    System.out.println("Sent " + events.size() + " events");
  }
}
