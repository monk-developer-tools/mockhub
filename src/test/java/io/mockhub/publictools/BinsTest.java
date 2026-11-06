package io.mockhub.publictools;

import static org.assertj.core.api.Assertions.assertThat;

import io.mockhub.support.MutableClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinsTest {

  @TempDir
  Path data;
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));

  private static PublicRequest call(String id, String rest, String body) {
    return new PublicRequest("POST", "/bins/" + id + rest, "a=1", Map.of("a", List.of("1")),
        Map.of("Content-Type", "application/json", "X-Forwarded-For", "203.0.113.9"), body, body.length(), "203.0.113.9");
  }

  private static Bins.Outcome ok() {
    return new Bins.Outcome("default", "", 200, 1, Map.of("Content-Type", "application/json"), "{\"ok\":true}", false);
  }

  private static void send(Bins bins, String id, int count) {
    for (int i = 0; i < count; i++) bins.record(id, call(id, "/n/" + i, "{\"n\":" + i + "}"), "/n/" + i, "", ok());
  }

  @Test
  void capturesRequestsAndShowsThemOnlyWithTheToken() {
    // Given
    Bins bins = new Bins(data, clock);
    Bins.Created bin = bins.create().orElseThrow();

    // When
    bins.record(bin.id(), call(bin.id(), "/hooks/paid", "{\"ok\":1}"), "/hooks/paid", "payment.paid", ok());

    // Then
    List<Bins.Captured> seen = bins.requests(bin.id(), bin.token(), 0, 0, 0).orElseThrow().requests();
    assertThat(seen).singleElement().satisfies(c -> {
      assertThat(c.path()).isEqualTo("/hooks/paid");
      assertThat(c.query()).isEqualTo("a=1");
      assertThat(c.body()).isEqualTo("{\"ok\":1}");
      assertThat(c.event()).isEqualTo("payment.paid");
      assertThat(c.outcome().by()).isEqualTo("default");
      assertThat(c.headers()).containsKey("Content-Type").doesNotContainKey("X-Forwarded-For");
    });
    assertThat(bins.requests(bin.id(), "wrong-token", 0, 0, 0)).isEmpty();
    assertThat(bins.requests(bin.id(), null, 0, 0, 0)).isEmpty();
  }

  @Test
  void aViewKeyReadsRequestsButCannotChangeTheBinAndSurvivesARestart() {
    // Given
    Bins bins = new Bins(data, clock);
    Bins.Created bin = bins.create().orElseThrow();
    Bins.Created other = bins.create().orElseThrow();
    send(bins, bin.id(), 2);
    String view = bins.viewKey(bin.id(), bin.token()).orElseThrow();

    // When
    bins.flush();
    Bins restarted = new Bins(data, clock);

    // Then
    assertThat(view).hasSize(32).isNotEqualTo(bin.token()).matches("[a-z0-9]{32}");
    assertThat(restarted.viewKey(bin.id(), bin.token())).contains(view);
    assertThat(restarted.requests(bin.id(), view, 0, 0, 0).orElseThrow().requests()).hasSize(2);
    assertThat(restarted.viewOnly(bin.id(), view)).isTrue();
    assertThat(restarted.viewOnly(bin.id(), bin.token())).isFalse();
    assertThat(restarted.viewOnly(other.id(), view)).isFalse();
    assertThat(restarted.settings(bin.id(), view)).isEmpty();
    assertThat(restarted.update(bin.id(), view, s -> s)).isFalse();
    assertThat(restarted.clear(bin.id(), view)).isFalse();
    assertThat(restarted.viewKey(bin.id(), view)).isEmpty();
  }

  @Test
  void keepsOnlyAFewLargeRequestsInMemoryButPagesThroughAllOfThem() {
    // Given
    Bins bins = new Bins(data, clock);
    Bins.Created bin = bins.create().orElseThrow();
    String large = "{\"pad\":\"" + "x".repeat(60 * 1024) + "\"}";

    // When
    for (int i = 0; i < 12; i++) bins.record(bin.id(), call(bin.id(), "/big/" + i, large), "/big/" + i, "", ok());

    // Then
    Bins.Page newest = bins.requests(bin.id(), bin.token(), 0, 0, 0).orElseThrow();
    assertThat(newest.requests()).hasSize(12);
    assertThat(newest.requests().get(0).path()).isEqualTo("/big/11");
    assertThat(newest.requests().get(11).path()).isEqualTo("/big/0");
    java.util.Deque<Bins.Captured> tail = new java.util.ArrayDeque<>(newest.requests());
    Bins.trimTail(tail, 1);
    assertThat(tail).hasSizeLessThan(6).isNotEmpty();
    assertThat(tail.getFirst().path()).isEqualTo("/big/11");
  }

  @Test
  void keepsEveryRequestAndPagesThroughOlderOnes() {
    // Given
    Bins bins = new Bins(data, clock);
    Bins.Created bin = bins.create().orElseThrow();
    send(bins, bin.id(), 250);

    // When
    Bins.Page newest = bins.requests(bin.id(), bin.token(), 0, 0, 0).orElseThrow();
    Bins.Page older = bins.requests(bin.id(), bin.token(), 0, newest.requests().get(newest.requests().size() - 1).seq(), 0).orElseThrow();
    Bins.Page oldest = bins.requests(bin.id(), bin.token(), 0, older.requests().get(older.requests().size() - 1).seq(), 0).orElseThrow();
    Bins.Page since = bins.requests(bin.id(), bin.token(), 245, 0, 0).orElseThrow();

    // Then
    assertThat(newest.total()).isEqualTo(250);
    assertThat(newest.requests()).hasSize(Bins.PAGE).first().extracting(Bins.Captured::seq).isEqualTo(250L);
    assertThat(older.requests()).hasSize(Bins.PAGE).first().extracting(Bins.Captured::seq).isEqualTo(150L);
    assertThat(oldest.requests()).hasSize(50).last().extracting(Bins.Captured::seq).isEqualTo(1L);
    assertThat(since.requests()).extracting(Bins.Captured::seq).containsExactly(250L, 249L, 248L, 247L, 246L);
  }

  @Test
  void cutsVeryLargeBodiesButKeepsTheirSize() {
    // Given
    Bins bins = new Bins(data, clock);
    Bins.Created bin = bins.create().orElseThrow();
    String big = "y".repeat(Bins.MAX_STORED_BODY + 10);

    // When
    bins.record(bin.id(), call(bin.id(), "", big), "/", "", ok());

    // Then
    Bins.Captured c = bins.requests(bin.id(), bin.token(), 0, 0, 0).orElseThrow().requests().get(0);
    assertThat(c.truncated()).isTrue();
    assertThat(c.body()).hasSize(Bins.MAX_STORED_BODY);
    assertThat(c.size()).isEqualTo(big.length());
  }

  @Test
  void keepsBinsTheirRulesAndRequestsAcrossARestart() {
    // Given
    Bins first = new Bins(data, clock);
    Bins.Created bin = first.create().orElseThrow();
    BinRule rule = BinRule.from(io.mockhub.api.Jsons.object().put("name", "Paid").set("answers",
        io.mockhub.api.Jsons.MAPPER.createArrayNode().add(io.mockhub.api.Jsons.object().put("status", 202))));
    first.update(bin.id(), bin.token(), s -> s.withRules(false, List.of(rule)));
    send(first, bin.id(), 60);

    // When
    Bins second = new Bins(data, clock);
    send(second, bin.id(), 1);

    // Then
    Bins.Settings settings = second.settings(bin.id(), bin.token()).orElseThrow();
    assertThat(settings.rulesEnabled()).isFalse();
    assertThat(settings.rules()).extracting(BinRule::name).containsExactly("Paid");
    Bins.Page page = second.requests(bin.id(), bin.token(), 0, 0, 0).orElseThrow();
    assertThat(page.total()).isEqualTo(61);
    assertThat(page.requests()).hasSize(61).first().extracting(Bins.Captured::seq).isEqualTo(61L);
  }

  @Test
  void dropsTheOldestFilesOnlyWhenABinOutgrowsItsSpace() throws Exception {
    // Given
    Bins bins = new Bins(data, clock, 2_000);
    Bins.Created bin = bins.create().orElseThrow();

    // When
    send(bins, bin.id(), 200);

    // Then
    try (var files = Files.list(data.resolve("mockhub/bins").resolve(bin.id()))) {
      assertThat(files.filter(p -> p.getFileName().toString().startsWith("requests-")).count()).isEqualTo(Bins.SEGMENTS);
    }
    Bins.Page page = bins.requests(bin.id(), bin.token(), 0, 0, 0).orElseThrow();
    assertThat(page.total()).isEqualTo(200);
    assertThat(page.oldestKept()).isGreaterThan(1);
    Bins.Page last = bins.requests(bin.id(), bin.token(), 0, page.oldestKept() + 1, 0).orElseThrow();
    assertThat(last.requests()).last().extracting(Bins.Captured::seq).isEqualTo(page.oldestKept());
  }

  @Test
  void clearingForgetsStoredRequests() {
    // Given
    Bins bins = new Bins(data, clock);
    Bins.Created bin = bins.create().orElseThrow();
    send(bins, bin.id(), 70);

    // When
    bins.clear(bin.id(), bin.token());
    send(bins, bin.id(), 1);

    // Then
    Bins.Page page = bins.requests(bin.id(), bin.token(), 0, 0, 0).orElseThrow();
    assertThat(page.requests()).extracting(Bins.Captured::seq).containsExactly(71L);
    assertThat(bins.requests(bin.id(), bin.token(), 0, 71, 0).orElseThrow().requests()).isEmpty();
  }

  @Test
  void deletesBinsAndTheirFilesAfterADayWithoutRequests() {
    // Given
    Bins bins = new Bins(data, clock);
    Bins.Created bin = bins.create().orElseThrow();
    send(bins, bin.id(), 3);
    clock.advance(Bins.IDLE.plus(Duration.ofMinutes(1)));

    // When
    bins.prune();

    // Then
    assertThat(bins.size()).isZero();
    assertThat(bins.settingsFor(bin.id())).isEmpty();
    assertThat(data.resolve("mockhub/bins").resolve(bin.id())).doesNotExist();
  }

  @Test
  void refusesNewBinsWhenFull() {
    // Given
    Bins bins = new Bins(data, clock);
    for (int i = 0; i < Bins.MAX_BINS; i++) bins.create();

    // When / Then
    assertThat(bins.create()).isEmpty();
  }
}
