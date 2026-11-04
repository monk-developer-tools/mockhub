package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BinsTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
  private final Bins bins = new Bins(clock);

  private static PublicRequest call(String id, String rest, String body) {
    return new PublicRequest("POST", "/bins/" + id + rest, "a=1", Map.of("a", List.of("1")),
        Map.of("Content-Type", "application/json", "X-Forwarded-For", "203.0.113.9"), body, body.length(), "203.0.113.9");
  }

  @Test
  void capturesRequestsAndShowsThemOnlyWithTheToken() {
    // Given
    Bins.Created bin = bins.create().orElseThrow();

    // When
    bins.capture(bin.id(), call(bin.id(), "/hooks/paid", "{\"ok\":1}"));

    // Then
    List<Bins.Captured> seen = bins.requests(bin.id(), bin.token(), 0).orElseThrow();
    assertThat(seen).singleElement().satisfies(c -> {
      assertThat(c.path()).isEqualTo("/hooks/paid");
      assertThat(c.query()).isEqualTo("a=1");
      assertThat(c.body()).isEqualTo("{\"ok\":1}");
      assertThat(c.headers()).containsKey("Content-Type").doesNotContainKey("X-Forwarded-For");
    });
    assertThat(bins.requests(bin.id(), "wrong-token", 0)).isEmpty();
    assertThat(bins.requests(bin.id(), null, 0)).isEmpty();
  }

  @Test
  void keepsTheNewestRequestsAndCutsLargeBodies() {
    // Given
    Bins.Created bin = bins.create().orElseThrow();

    // When
    for (int i = 0; i < Bins.MAX_REQUESTS + 5; i++) bins.capture(bin.id(), call(bin.id(), "", "x"));
    bins.capture(bin.id(), call(bin.id(), "", "y".repeat(Bins.MAX_STORED_BODY + 10)));

    // Then
    List<Bins.Captured> seen = bins.requests(bin.id(), bin.token(), 0).orElseThrow();
    assertThat(seen).hasSize(Bins.MAX_REQUESTS);
    assertThat(seen.get(0).truncated()).isTrue();
    assertThat(seen.get(0).body()).hasSize(Bins.MAX_STORED_BODY);
    assertThat(bins.requests(bin.id(), bin.token(), seen.get(1).seq())).hasValueSatisfying(l -> assertThat(l).hasSize(1));
  }

  @Test
  void answersWithTheReplyTheOwnerSet() {
    // Given
    Bins.Created bin = bins.create().orElseThrow();
    bins.setReply(bin.id(), bin.token(), new Bins.Reply(202, "text/plain", "thanks"));

    // When
    Bins.Reply reply = bins.capture(bin.id(), call(bin.id(), "", "")).orElseThrow();

    // Then
    assertThat(reply).isEqualTo(new Bins.Reply(202, "text/plain", "thanks"));
    assertThat(bins.setReply(bin.id(), "nope", Bins.Reply.DEFAULT)).isFalse();
  }

  @Test
  void deletesBinsAfterADayWithoutRequests() {
    // Given
    Bins.Created bin = bins.create().orElseThrow();
    clock.advance(Bins.IDLE.plus(Duration.ofMinutes(1)));

    // When
    bins.prune();

    // Then
    assertThat(bins.size()).isZero();
    assertThat(bins.capture(bin.id(), call(bin.id(), "", ""))).isEmpty();
  }

  @Test
  void refusesNewBinsWhenFull() {
    // Given
    for (int i = 0; i < Bins.MAX_BINS; i++) bins.create();

    // When / Then
    assertThat(bins.create()).isEmpty();
  }
}
