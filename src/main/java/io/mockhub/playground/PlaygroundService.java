package io.mockhub.playground;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class PlaygroundService {
  static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

  private final HttpSender sender;
  private final TargetGuard guard;
  private final PlaygroundHistory history;
  private final Supplier<String> localBase;
  private final Clock clock;

  public PlaygroundService(HttpSender sender, TargetGuard guard, PlaygroundHistory history, Supplier<String> localBase, Clock clock) {
    this.sender = sender;
    this.guard = guard;
    this.history = history;
    this.localBase = localBase;
    this.clock = clock;
  }

  public PlaygroundEntry send(String user, PlaygroundRequest request, Set<String> selfHosts) {
    if (!METHODS.contains(request.method())) throw new PlaygroundException(request.method() + " isn't supported");
    if (request.url().isEmpty()) throw new PlaygroundException("Enter a URL, e.g. https://api.example.com/users or /payments/42");
    Target target = target(request.url(), selfHosts);
    if (!target.local()) guard.check(target.uri());
    String id = UUID.randomUUID().toString();
    String at = Instant.now(clock).toString();
    long started = System.nanoTime();
    PlaygroundEntry entry;
    try {
      HttpSender.Raw raw = sender.send(target.uri(), request.method(), request.headers(), request.body(), TIMEOUT, MAX_RESPONSE_BYTES);
      long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
      entry = new PlaygroundEntry(id, at, request,
          new PlaygroundResponse(raw.status(), raw.headers(), text(raw.body()), raw.body().length, raw.truncated(), ms), null, target.local());
    } catch (Exception e) {
      entry = new PlaygroundEntry(id, at, request, null, describe(e), target.local());
    }
    history.add(user, entry);
    return entry;
  }

  record Target(URI uri, boolean local) {}

  Target target(String url, Set<String> selfHosts) {
    if (url.startsWith("/")) return local(url);
    URI uri;
    try {
      uri = URI.create(url.contains("://") ? url : "https://" + url);
    } catch (IllegalArgumentException e) {
      throw new PlaygroundException("That isn't a valid URL");
    }
    String authority = uri.getRawAuthority() == null ? "" : uri.getRawAuthority().toLowerCase(Locale.ROOT);
    if (selfHosts.contains(authority)) {
      String rest = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
          + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
      return local(rest);
    }
    return new Target(uri, false);
  }

  private Target local(String pathAndQuery) {
    if (pathAndQuery.startsWith("/__admin")) {
      throw new PlaygroundException("The playground calls mocks and other APIs. Use MockHub's API directly for /__admin.");
    }
    try {
      return new Target(URI.create(localBase.get() + pathAndQuery), true);
    } catch (IllegalArgumentException e) {
      throw new PlaygroundException("That isn't a valid path");
    }
  }

  static String text(byte[] body) {
    String s = new String(body, StandardCharsets.UTF_8);
    return s.indexOf('\u0000') >= 0 ? "[binary content, " + body.length + " bytes]" : s;
  }

  private static String describe(Exception e) {
    String type = e.getClass().getSimpleName();
    if (type.contains("Timeout")) return "No response within " + TIMEOUT.toSeconds() + " seconds";
    if (type.contains("ConnectException")) return "Couldn't connect: the server refused the connection or isn't reachable";
    if (type.contains("SSL")) return "TLS handshake failed: " + e.getMessage();
    String message = e.getMessage();
    return message == null || message.isBlank() ? "The request failed (" + type + ")" : message;
  }
}
