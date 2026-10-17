package io.mockhub.playground;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaygroundServiceTest {

  @TempDir
  Path dir;
  private final List<URI> sent = new ArrayList<>();
  private HttpSender.Raw reply = new HttpSender.Raw(200, List.of(new Header("Content-Type", "application/json")), "{\"ok\":true}".getBytes(StandardCharsets.UTF_8), false);
  private Exception failure;
  private PlaygroundHistory history;
  private PlaygroundService service;

  @BeforeEach
  void setUp() {
    HttpSender sender = (URI uri, String method, List<Header> headers, String body, Duration timeout, int maxBytes) -> {
      sent.add(uri);
      if (failure != null) throw failure;
      return reply;
    };
    history = new PlaygroundHistory(dir);
    TargetGuard guard = new TargetGuard(false, host -> new InetAddress[] {InetAddress.getByName(host.equals("intranet") ? "10.0.0.1" : "93.184.216.34")});
    service = new PlaygroundService(sender, guard, history, () -> "http://127.0.0.1:8080", Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneOffset.UTC));
  }

  private static PlaygroundRequest get(String url) {
    return new PlaygroundRequest("get", url, List.of(new Header("Accept", "application/json")), "");
  }

  @Test
  void callsAPublicApiAndSavesTheExchange() {
    // When
    PlaygroundEntry entry = service.send("u1", get("https://api.example.com/users?page=2"), Set.of());

    // Then
    assertThat(sent).containsExactly(URI.create("https://api.example.com/users?page=2"));
    assertThat(entry.request().method()).isEqualTo("GET");
    assertThat(entry.response().status()).isEqualTo(200);
    assertThat(entry.response().body()).isEqualTo("{\"ok\":true}");
    assertThat(entry.local()).isFalse();
    assertThat(history.list("u1")).extracting(PlaygroundEntry::id).containsExactly(entry.id());
  }

  @Test
  void sendsPathsAndThisServersOwnUrlsToTheLocalMocks() {
    // When
    service.send("u1", get("/shop/orders/1"), Set.of());
    PlaygroundEntry viaHost = service.send("u1", get("https://mocks.acme.dev/shop/orders?x=1"), Set.of("mocks.acme.dev"));

    // Then
    assertThat(sent).containsExactly(URI.create("http://127.0.0.1:8080/shop/orders/1"), URI.create("http://127.0.0.1:8080/shop/orders?x=1"));
    assertThat(viaHost.local()).isTrue();
  }

  @Test
  void refusesTheAdminApiAndPrivateHosts() {
    // When / Then
    assertThatThrownBy(() -> service.send("u1", get("/__admin/mappings"), Set.of())).isInstanceOf(PlaygroundException.class);
    assertThatThrownBy(() -> service.send("u1", get("http://intranet/secrets"), Set.of())).hasMessageContaining("private or local");
    assertThat(sent).isEmpty();
  }

  @Test
  void recordsConnectionFailuresAsErrors() {
    // Given
    failure = new ConnectException("refused");

    // When
    PlaygroundEntry entry = service.send("u1", get("https://api.example.com/down"), Set.of());

    // Then
    assertThat(entry.response()).isNull();
    assertThat(entry.error()).startsWith("Couldn't connect");
    assertThat(history.list("u1")).hasSize(1);
  }

  @Test
  void describesBinaryBodiesInsteadOfShowingThem() {
    // Given
    reply = new HttpSender.Raw(200, List.of(), new byte[] {0x25, 0x50, 0x00, 0x01}, false);

    // When
    PlaygroundEntry entry = service.send("u1", get("https://api.example.com/file.pdf"), Set.of());

    // Then
    assertThat(entry.response().body()).isEqualTo("[binary content, 4 bytes]");
  }

  @Test
  void rejectsUnknownMethodsAndEmptyUrls() {
    // When / Then
    assertThatThrownBy(() -> service.send("u1", new PlaygroundRequest("TRACE", "https://a.example", null, null), Set.of())).isInstanceOf(PlaygroundException.class);
    assertThatThrownBy(() -> service.send("u1", get(" "), Set.of())).hasMessageContaining("Enter a URL");
  }
}
