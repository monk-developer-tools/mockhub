package io.mockhub.callout;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.mockhub.playground.Header;
import io.mockhub.playground.HttpSender;
import io.mockhub.stub.StubMetadata;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CalloutService {
  public static final String ASYNC = "async";
  public static final String SYNC = "sync";
  static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
  static final Duration TIMEOUT = Duration.ofSeconds(30);

  public record Outcome(CalloutEntry entry, byte[] body, boolean retryable) {}

  public interface Pause {
    void sleep(long millis) throws InterruptedException;
  }

  private final CalloutRenderer renderer;
  private final CalloutTargets targets;
  private final HttpSender sender;
  private final CalloutLog log;
  private final Clock clock;
  private final CalloutAuth auth;
  private final Pause pause;

  public CalloutService(CalloutRenderer renderer, CalloutTargets targets, HttpSender sender, CalloutLog log, Clock clock) {
    this(renderer, targets, sender, log, clock, CalloutAuth.NONE, Thread::sleep);
  }

  public CalloutService(CalloutRenderer renderer, CalloutTargets targets, HttpSender sender, CalloutLog log, Clock clock,
      CalloutAuth auth, Pause pause) {
    this.renderer = renderer;
    this.targets = targets;
    this.sender = sender;
    this.log = log;
    this.clock = clock;
    this.auth = auth;
    this.pause = pause;
  }

  public Outcome call(CalloutSpec spec, ServeEvent event, String mode) {
    Outcome outcome = attempt(spec, event, mode, 1);
    while (shouldRetry(spec, outcome)) {
      int next = outcome.entry().attempt() + 1;
      try {
        pause.sleep(spec.retry().backoffBefore(next));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return outcome;
      }
      outcome = attempt(spec, event, mode, next);
    }
    return outcome;
  }

  public boolean shouldRetry(CalloutSpec spec, Outcome outcome) {
    return outcome.retryable() && outcome.entry().attempt() <= spec.retry().count();
  }

  public Outcome attempt(CalloutSpec spec, ServeEvent event, String mode, int attempt) {
    StubMapping stub = event.getStubMapping();
    String projectId = stub == null ? null : StubMetadata.projectId(stub);
    String at = Instant.now(clock).toString();
    CalloutEntry.Trigger trigger = new CalloutEntry.Trigger(event.getRequest().getMethod().getName(), event.getRequest().getUrl());
    OutboundRequest request = renderer.unrendered(spec);
    long started = System.nanoTime();
    CalloutEntry.Result result = null;
    String error = null;
    byte[] body = new byte[0];
    boolean retryable = false;
    try {
      request = renderer.render(spec, event, SYNC.equals(mode) ? null : event.getResponse());
      List<Header> sent = request.headers();
      if (spec.connection() != null) {
        Header credential = auth.header(projectId, spec.connection());
        sent = with(request.headers(), credential);
        request = new OutboundRequest(request.method(), request.url(),
            with(request.headers(), new Header(credential.name(), "\u2022\u2022\u2022 (connection " + spec.connection() + ")")), request.body());
      }
      CalloutTargets.Target target = targets.resolve(request.url(), event.getRequest());
      HttpSender.Raw raw = sender.send(target.uri(), request.method(), sent, request.body(), TIMEOUT, MAX_RESPONSE_BYTES);
      body = raw.body();
      result = new CalloutEntry.Result(raw.status(), raw.headers(), text(raw.body()), raw.truncated());
      retryable = spec.retry().retriesStatus(raw.status());
    } catch (CalloutException e) {
      error = e.getMessage();
    } catch (Exception e) {
      error = describe(e);
      retryable = true;
    }
    long ms = Duration.ofNanos(System.nanoTime() - started).toMillis();
    CalloutEntry entry = new CalloutEntry(UUID.randomUUID().toString(), at,
        projectId,
        stub == null || stub.getId() == null ? null : stub.getId().toString(),
        stub == null ? null : stub.getName(),
        mode, trigger, request, result, error, ms, event.getId() == null ? null : event.getId().toString(), attempt);
    log.add(entry);
    return new Outcome(entry, body, retryable);
  }

  private static List<Header> with(List<Header> headers, Header added) {
    List<Header> out = new ArrayList<>();
    String name = added.name().toLowerCase(Locale.ROOT);
    for (Header h : headers) {
      if (!h.name().toLowerCase(Locale.ROOT).equals(name)) out.add(h);
    }
    out.add(added);
    return out;
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
    return message == null || message.isBlank() ? "The callout failed (" + type + ")" : message;
  }
}
