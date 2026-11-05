package io.mockhub.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.mockhub.auth.Principal;
import io.mockhub.auth.Role;
import io.mockhub.auth.TokenGrant;
import io.mockhub.playground.Header;
import io.mockhub.support.MutableClock;
import io.mockhub.user.User;
import io.mockhub.user.UserSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TunnelHubTest {

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-05T10:00:00Z"));
  private final TunnelHub hub = new TunnelHub((principal, projectId) -> !projectId.equals("locked"), scheduler, clock);
  private final Principal alex = person("u1", "alex", Role.EDITOR);
  private final Principal sam = person("u2", "sam", Role.EDITOR);

  @AfterEach
  void stop() {
    scheduler.shutdownNow();
  }

  private static Principal person(String id, String name, Role role) {
    return Principal.of(new User.Builder().id(id).username(name).source(UserSource.LOCAL).serverRole(role).build());
  }

  private CompletableFuture<TunnelMessages.Response> sendAsync(String url, Duration timeout) {
    return CompletableFuture.supplyAsync(() -> hub.send("p1", URI.create(url), "GET", List.of(new Header("Accept", "text/plain")), new byte[0], timeout));
  }

  private static TunnelMessages.Response ok(String body) {
    return new TunnelMessages.Response(200, List.of(new Header("Content-Type", "text/plain")), body.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void aWaitingPollReceivesTheRequestAndTheSenderGetsTheAnswer() throws Exception {
    // Given
    CompletableFuture<TunnelMessages.Request> poll = hub.next("laptop", alex);

    // When
    CompletableFuture<TunnelMessages.Response> answer = sendAsync("tunnel://laptop/api/orders?id=7", Duration.ofSeconds(5));
    TunnelMessages.Request request = poll.get(5, TimeUnit.SECONDS);
    hub.respond("laptop", alex, request.id(), ok("hello"));

    // Then
    assertThat(request.method()).isEqualTo("GET");
    assertThat(request.path()).isEqualTo("/api/orders?id=7");
    assertThat(request.headers()).containsExactly(new Header("Accept", "text/plain"));
    assertThat(new String(answer.get(5, TimeUnit.SECONDS).body(), StandardCharsets.UTF_8)).isEqualTo("hello");
  }

  @Test
  void aRequestSentBetweenPollsWaitsForTheNextPoll() throws Exception {
    // Given
    hub.next("laptop", alex).complete(null);
    CompletableFuture<TunnelMessages.Response> answer = sendAsync("tunnel://laptop", Duration.ofSeconds(5));
    Thread.sleep(200);

    // When
    TunnelMessages.Request request = hub.next("laptop", alex).get(5, TimeUnit.SECONDS);
    hub.respond("laptop", alex, request.id(), ok("late"));

    // Then
    assertThat(request.path()).isEqualTo("/");
    assertThat(answer.get(5, TimeUnit.SECONDS).status()).isEqualTo(200);
  }

  @Test
  void sendingToATunnelNobodyOpenedFailsWith502() {
    // Given / When / Then
    assertThatThrownBy(() -> hub.send("p1", URI.create("tunnel://ghost/x"), "GET", List.of(), new byte[0], Duration.ofSeconds(1)))
        .isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(502));
  }

  @Test
  void aTunnelGoesOfflineWhenItStopsPolling() {
    // Given
    hub.next("laptop", alex).complete(null);

    // When
    clock.advance(TunnelHub.ONLINE_GRACE.plusSeconds(1));

    // Then
    assertThat(hub.isOnline("laptop")).contains(false);
    assertThatThrownBy(() -> hub.send("p1", URI.create("tunnel://laptop/x"), "GET", List.of(), new byte[0], Duration.ofSeconds(1)))
        .isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(502));
  }

  @Test
  void aProjectTheOwnerCannotEditIsRefused() {
    // Given
    hub.next("laptop", alex);

    // When / Then
    assertThatThrownBy(() -> hub.send("locked", URI.create("tunnel://laptop/x"), "GET", List.of(), new byte[0], Duration.ofSeconds(1)))
        .isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(403));
  }

  @Test
  void aBodyOverTheLimitIsRefused() {
    // Given
    hub.next("laptop", alex);

    // When / Then
    assertThatThrownBy(() -> hub.send("p1", URI.create("tunnel://laptop/x"), "POST", List.of(), new byte[TunnelHub.MAX_BODY + 1], Duration.ofSeconds(1)))
        .isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(413));
  }

  @Test
  void anUnansweredRequestTimesOutWith504AndCountsAsFailed() {
    // Given
    hub.next("laptop", alex);

    // When / Then
    assertThatThrownBy(() -> hub.send("p1", URI.create("tunnel://laptop/x"), "GET", List.of(), new byte[0], Duration.ofMillis(200)))
        .isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(504));
    JsonNode listed = hub.list(alex).get(0);
    assertThat(listed.get("requests").asInt()).isEqualTo(1);
    assertThat(listed.get("failed").asInt()).isEqualTo(1);
    assertThat(listed.get("waiting").asInt()).isZero();
  }

  @Test
  void aNameHeldByAnotherUserIsRefused() {
    // Given
    hub.next("laptop", alex);

    // When / Then
    assertThatThrownBy(() -> hub.next("laptop", sam))
        .isInstanceOfSatisfying(TunnelException.class, e -> {
          assertThat(e.status()).isEqualTo(409);
          assertThat(e.getMessage()).contains("in use by alex");
        });
  }

  @Test
  void badNamesViewersAndReviewTokensCannotOpenATunnel() {
    // Given
    Principal viewer = person("u3", "vic", Role.VIEWER);
    Principal reviewer = Principal.viaToken(new User.Builder().id("u4").username("rae").source(UserSource.LOCAL).serverRole(Role.EDITOR).build(),
        new TokenGrant("t1", "review", Role.EDITOR, List.of(), true));

    // When / Then
    assertThatThrownBy(() -> hub.next("Laptop!", alex)).isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(400));
    assertThatThrownBy(() -> hub.next("laptop", viewer)).isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(403));
    assertThatThrownBy(() -> hub.next("laptop", reviewer)).isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(403));
  }

  @Test
  void eachUserMayOpenALimitedNumberOfTunnels() {
    // Given
    for (int i = 0; i < TunnelHub.MAX_PER_USER; i++) hub.next("t" + i, alex);

    // When / Then
    assertThatThrownBy(() -> hub.next("one-more", alex)).isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(429));
  }

  @Test
  void closingATunnelEndsItsPollAndItsWaitingRequests() {
    // Given
    CompletableFuture<TunnelMessages.Request> poll = hub.next("laptop", alex);

    // When
    hub.close("laptop", alex);

    // Then
    assertThat(poll).isCompletedExceptionally();
    assertThat(hub.list(alex)).isEmpty();
    assertThatThrownBy(() -> hub.respond("laptop", alex, "x", ok("")))
        .isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(410));
  }

  @Test
  void revokingTheTokenClosesTheTunnelsItOpened() {
    // Given
    User user = new User.Builder().id("u1").username("alex").source(UserSource.LOCAL).serverRole(Role.EDITOR).build();
    hub.next("laptop", Principal.viaToken(user, new TokenGrant("tok-1", "laptop", Role.EDITOR, List.of(), false)));
    hub.next("desk", alex);

    // When
    hub.onTokenRevoked("tok-1");

    // Then
    assertThat(hub.list(alex)).extracting(n -> n.get("name").asText()).containsExactly("desk");
  }

  @Test
  void usersSeeOnlyTheirOwnTunnelsAndAdminsSeeAll() {
    // Given
    hub.next("laptop", alex);
    hub.next("desk", sam);

    // When
    JsonNode forAlex = hub.list(alex);
    JsonNode forAdmin = hub.list(person("u9", "root", Role.ADMIN));

    // Then
    assertThat(forAlex).extracting(n -> n.get("name").asText()).containsExactly("laptop");
    assertThat(forAdmin).extracting(n -> n.get("owner").asText()).containsExactly("sam", "alex");
    assertThatThrownBy(() -> hub.close("desk", alex)).isInstanceOfSatisfying(TunnelException.class, e -> assertThat(e.status()).isEqualTo(404));
  }
}
